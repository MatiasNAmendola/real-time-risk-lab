---
title: Barrier / Shadow / Circuit Modes — estrategias de deployment del Risk Engine
tags: [concept, pattern/resilience, barrier, shadow-mode, circuit-breaker, deployment]
created: 2026-05-12
source_archive: docs/25-barrier-shadow-circuit-modes.md (migrado 2026-05-12)
---

# Barrier / Shadow / Circuit Modes — estrategias de deployment del Risk Engine

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

El hilo de la request se bloquea hasta que el engine devuelve una decisión. Si el engine supera `timeout_ms`, se usa `fallback_decision` (por defecto `REVIEW`).

### Cuándo usarlo

- Producción cuando la latencia del engine es predecible y está dentro del SLO del endpoint.
- Cuando las consecuencias de aprobar erróneamente superan el impacto de la latencia adicional (fraud prevention, AML).
- Cuando el ruleset ya fue validado en shadow y está estable.

### Tradeoff

Latencia de engine es latencia de API.

---

## Modo 2: Shadow Mode

**Clase**: `ShadowMode`

Responde inmediatamente con `APPROVE` al caller. En background (virtual thread), evalúa el ruleset real y publica la decisión sombra al `shadowPublisher` (Kafka topic `risk-shadow-decisions` en producción).

```java
// Respuesta inmediata al caller
AggregateDecision immediate = new AggregateDecision(
        RuleAction.APPROVE, List.of(), engine.activeConfigHash(), ...);

// Evaluación real en background
executor.submit(() -> {
    AggregateDecision shadow = engine.evaluate(snapshot);
    shadowPublisher.accept(shadow);
});
return immediate;
```

### Cuándo usarlo

- Rollout de un nuevo ruleset: querés ver cómo hubiera decidido sin impactar conversiones.
- A/B testing entre dos rulesets sin afectar los clientes.
- Canary deployment: activar shadow en un 5% del tráfico para comparar contra blocking en el 95%.

### Tradeoff

Todos los usuarios pasan durante este período, incluyendo transacciones que el engine hubiera bloqueado.

---

## Modo 3: Circuit Mode

**Clase**: `CircuitMode`

Evalúa normalmente hasta que detecta que el p99 de latencia del engine en la ventana deslizante de 1 minuto supera el umbral (default 250ms). Cuando el circuito está abierto, todas las evaluaciones son cortocircuitadas a `REVIEW` sin invocar el engine.

### Cuándo usarlo

- Producción cuando el engine puede tener picos de latencia (GC pause, lock contention).
- Cuando preferís REVIEW sobre APPROVE como fallback.
- Cuando el SLO del endpoint tiene un presupuesto de error pequeño.

### Configuración en YAML

```yaml
barrier_mode: circuit
circuit:
  p99_threshold_ms: 150   # abre el circuito si p99 > 150ms
  window_seconds: 60      # ventana deslizante
```

### Tradeoff

Mientras el circuito está abierto, todas las transacciones son `REVIEW`. El sliding window es in-memory — en un cluster con múltiples instancias, cada instancia tiene su propio estado del circuito.

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

## Archivos de implementación

- `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/mode/BarrierMode.java`
- `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/mode/BlockingBarrier.java`
- `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/mode/ShadowMode.java`
- `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/mode/CircuitMode.java`

## Related

- [[0046-declarative-rules-engine]] — decisiones de diseño del engine, incluyendo hot reload.
- [[Rules-Engine]] — modelo conceptual y tipos de reglas.
- [[Circuit-Breaker]] — patrón de circuit breaker general.
- [[Latency-Budget]] — presupuesto de latencia que motiva el circuit mode.
- [[SLI-SLO-Error-Budget]] — SLOs que estos modos protegen.
- [[Risk-Platform-Overview]]
