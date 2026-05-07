# Barrier / Shadow / Circuit Modes — Risk Engine Deployment Strategies

## Por qué existen estos tres modos

El motor de reglas declarativo (`pkg/risk-domain`) puede integrarse en tres modos distintos de operación. Cada modo responde a una pregunta diferente de deployment:

- **Barrier**: ¿necesito una decisión real que bloquee la transacción?
- **Shadow**: ¿quiero validar un nuevo ruleset sin impactar el tráfico?
- **Circuit**: ¿quiero protegerme de un engine degradado sin caer en modo shadow permanente?

Todos implementan la misma interfaz `BarrierMode`:

```java
public interface BarrierMode {
    AggregateDecision evaluate(FeatureSnapshot snapshot);
    String modeName();
}
```

El modo activo se selecciona via la variable de entorno `BARRIER_MODE` (`blocking` | `shadow` | `circuit`). Sin configuración, el default es `blocking`.

---

## Modo 1: Blocking Barrier (default)

**Clase**: `BlockingBarrier`

### Comportamiento

El hilo de la request se bloquea hasta que el engine devuelve una decisión. Si el engine supera `timeout_ms` (configurado en `rules.yaml`), se usa `fallback_decision` (por defecto `REVIEW`).

```
Request → BlockingBarrier.evaluate() → RuleEngineImpl.evaluate() → AggregateDecision → Response
```

### Cuándo usarlo

- Producción cuando la latencia del engine es predecible y está dentro del SLO del endpoint
- Cuando las consecuencias de aprobar erróneamente una transacción superan el impacto de la latencia adicional (fraud prevention, AML)
- Cuando el ruleset ya fue validado en shadow y está estable

### Garantías

- La decisión siempre refleja las reglas activas al momento de la evaluación
- El hot reload (`AtomicReference`) garantiza que mid-stream evaluations usan el ruleset que tenían al inicio (no el recién recargado)
- El timeout actúa como safety net: si el engine supera `timeout_ms`, la decisión es `fallback_decision` y el audit trail lo registra con `timedOut: true`

### Tradeoff

Latencia de engine es latencia de API. Si el p99 del engine sube de 10ms a 200ms, el p99 de `/risk` sube igual.

---

## Modo 2: Shadow Mode

**Clase**: `ShadowMode`

### Comportamiento

Responde inmediatamente con `APPROVE` al caller. En background (virtual thread), evalúa el ruleset real y publica la decisión sombra al `shadowPublisher` (Kafka topic `risk-shadow-decisions` en producción).

```
Request → ShadowMode.evaluate() → return APPROVE immediately
                                 → (background VT) RuleEngineImpl.evaluate() → shadowPublisher
```

### Cuándo usarlo

- Rollout de un nuevo ruleset: querés ver cómo hubiera decidido sin impactar conversiones
- A/B testing entre dos rulesets sin afectar los clientes
- Fase de calibración: validar que las reglas de velocidad (`velocity` rule) no tienen falsos positivos masivos antes de activarlas
- Canary deployment: activar shadow en un 5% del tráfico para comparar contra blocking en el 95%

### Garantías

- El caller nunca espera ni recibe una decisión adversa mientras está en shadow
- Las decisiones sombra son eventualmente consistentes y llegan al publisher con una latencia adicional
- Los errores en la evaluación background son silenciosos (logueados, no propagados)

### Tradeoff

- Todos los usuarios pasan durante este período, incluyendo transacciones que el engine hubiera bloqueado
- El throughput del executor (virtual threads) está limitado por la capacidad del publisher downstream
- Si el publisher está caído, las decisiones sombra se pierden (no hay persistencia local)

### Implementación clave

```java
// Respuesta inmediata al caller
AggregateDecision immediate = new AggregateDecision(
        RuleAction.APPROVE, List.of(),
        engine.activeConfigHash(), engine.activeConfig().version(),
        false, null, 0L);

// Evaluación real en background
executor.submit(() -> {
    AggregateDecision shadow = engine.evaluate(snapshot);
    shadowPublisher.accept(shadow);
});
return immediate;
```

El `ExecutorService` usa `Executors.newVirtualThreadPerTaskExecutor()` — sin pool size limit. En alta concurrencia esto puede presionar al publisher downstream.

---

## Modo 3: Circuit Mode

**Clase**: `CircuitMode`

### Comportamiento

Evalúa normalmente hasta que detecta que el p99 de latencia del engine en la ventana deslizante de 1 minuto supera el umbral (default 250ms). Cuando el circuito está abierto, todas las evaluaciones son cortocircuitadas a `REVIEW` sin invocar el engine.

```
Request → CircuitMode.evaluate() → isCircuitOpen()? 
                                     true  → return REVIEW (no engine call)
                                     false → RuleEngineImpl.evaluate() → record latency
```

El circuito se resetea automáticamente cuando el p99 vuelve a bajar del umbral (ventana deslizante, no hay reset manual).

### Cuándo usarlo

- Producción cuando el engine puede tener picos de latencia (GC pause, lock contention en un ruleset grande)
- Cuando preferís REVIEW sobre APPROVE como fallback (diferencia respecto a shadow)
- Cuando el SLO del endpoint tiene un presupuesto de error pequeño y no podés aceptar cola de requests esperando

### Garantías

- El p99 del endpoint nunca puede ser mayor que el umbral configurado + overhead de red
- Las decisiones emitidas mientras el circuito está abierto (`timedOut: true`) quedan registradas en el audit trail para análisis posterior
- El circuito no tiene estado persistido: si el proceso se reinicia, arranca cerrado

### Tradeoff

- Mientras el circuito está abierto, todas las transacciones son `REVIEW` — including las que el engine hubiera aprobado sin dudar. Esto puede generar trabajo manual de revisión
- El sliding window de latencia es in-memory. En un cluster con múltiples instancias, cada instancia tiene su propio estado del circuito
- Para producción real se necesita integrar con Micrometer + un circuit breaker externo (Resilience4j) para tener estado compartido

### Configuración en YAML

```yaml
barrier_mode: circuit
circuit:
  p99_threshold_ms: 150   # abre el circuito si p99 > 150ms
  window_seconds: 60      # ventana deslizante
```

---

## Comparación rápida

| Dimensión           | Blocking Barrier          | Shadow Mode              | Circuit Mode              |
|---------------------|---------------------------|--------------------------|---------------------------|
| Decisión al caller  | Real (APPROVE/DECLINE/...) | Siempre APPROVE          | Real o REVIEW (fallback)  |
| Impacto en latencia | Engine latency = API latency | Cero (async)            | Bounded por umbral p99    |
| Efecto sobre fraude | Bloquea fraude real        | No bloquea nada          | Bloquea cuando circuito cerrado |
| Uso típico          | Producción estable         | Rollout / calibración    | Producción con SLO estricto |
| Fallback            | `fallback_decision` (REVIEW)| APPROVE siempre          | REVIEW cuando circuito abierto |
| Observabilidad      | Audit trail por decisión   | Shadow topic + audit      | Audit + circuit open events |

---

## Selección del modo en el código

En el PoC, la selección se hace en la clase de bootstrap del PoC:

```java
String mode = System.getenv().getOrDefault("BARRIER_MODE", "blocking");
BarrierMode barrierMode = switch (mode) {
    case "shadow"  -> new ShadowMode(ruleEngine, shadowPublisher);
    case "circuit" -> new CircuitMode(ruleEngine, 250L);
    default        -> new BlockingBarrier(ruleEngine);
};
```

---

## Key Design Principle

> "Nuestro motor de reglas soporta tres modos de operación: blocking para produccion normal donde la decisión bloquea la request, shadow para validar un nuevo ruleset en background sin afectar conversiones, y circuit que abre un fallback a REVIEW cuando el p99 del engine supera el umbral, protegiendo el SLO del endpoint. El modo se cambia via variable de entorno sin redeploy, y el hot reload del ruleset funciona igual en los tres modos gracias al AtomicReference que garantiza evaluaciones in-flight consistentes."

---

## Archivos de implementacion

- `pkg/risk-domain/src/main/java/com/naranjax/poc/risk/mode/BarrierMode.java` — interfaz comun
- `pkg/risk-domain/src/main/java/com/naranjax/poc/risk/mode/BlockingBarrier.java`
- `pkg/risk-domain/src/main/java/com/naranjax/poc/risk/mode/ShadowMode.java`
- `pkg/risk-domain/src/main/java/com/naranjax/poc/risk/mode/CircuitMode.java`
- `pkg/risk-domain/src/test/java/com/naranjax/poc/risk/mode/BarrierModeTest.java` — tests de los tres modos
