# 18 — Declarative Fraud Rules Engine: Design

## Contexto y objetivos

### Por qué declarativo

Las reglas de fraude responden a dos drivers que IT no controla:

1. **Presion regulatoria**: el BCRA o la red (Visa/MC) emiten circulares con plazos de horas. Esperar un release cycle significa incumplimiento.
2. **Descubrimiento de patrones**: el equipo de Riesgo corre analisis ad-hoc y detecta un patron nuevo (ej. chargebacks concentrados en MCC 7995 los lunes). Necesitan activar la regla ese dia, no en dos semanas.

Si las reglas viven en codigo Java, cada cambio pasa por: PR review, CI, artefacto, deploy. Eso es incompatible con la velocidad que requiere la operacion de fraude en una fintech.

**El contrato es**: Riesgo define la politica, IT construye el interprete. El interprete no cambia; la politica cambia todo el tiempo.

### Restricciones operativas

| Constraint | Valor |
|---|---|
| Throughput objetivo | 150 TPS |
| Latencia p99 | 300ms (budget total del request) |
| Budget del rules engine | < 10ms p99 (< 3.3% del budget total) |
| Auditabilidad | Reconstruir decision N meses despues con la version exacta de reglas usada |
| Hot reload | Sin reinicio; decisiones in-flight no deben ver estado inconsistente |

---

## Modelo conceptual

```
Rule {
  name:       string          -- identificador unico
  version:    semver          -- version de esta regla (no del config global)
  enabled:    bool            -- toggle sin borrar
  type:       RuleType        -- determina el interprete a usar
  parameters: object          -- especificos al tipo
  weight:     float           -- para scoring ponderado
  action:     RuleAction      -- DECLINE | REVIEW | FLAG | ALLOW
  metadata: {
    owner:              string   -- equipo duenio
    created_at:         iso8601
    last_modified_by:   string
    last_modified_at:   iso8601
    deployment_env:     string[] -- ["dev","staging","prod"]
    audit_trail:        AuditEntry[]
  }
}

RuleType     = "threshold" | "combination" | "velocity" | "chargeback_history"
             | "international" | "time_of_day" | "merchant_category"
             | "device_fingerprint" | "allowlist"

RuleAction   = "DECLINE" | "REVIEW" | "FLAG" | "ALLOW"

RuleConfig {
  version:              semver
  hash:                 string      -- sha256 del contenido canonico
  deployed_at:          iso8601
  deployed_by:          string
  environment:          string
  aggregation_policy:   AggregationPolicy
  timeout_ms:           int
  fallback_decision:    RuleAction
  rules:                Rule[]
  audit:                AuditEntry[]
}
```

La separacion entre `RuleConfig.version` (version del config global) y `Rule.version` (version de cada regla individual) permite rastrear que una regla especifica cambio sin asumir que todo el archivo fue modificado.

---

## Tipos de reglas soportados

### 1. `threshold`

Evalua un campo numerico del request contra un valor con un operador.

```
parameters:
  field:    string     -- path en RiskRequest o FeatureSnapshot
  operator: ">" | ">=" | "<" | "<=" | "==" | "!="
  value:    number
```

Ejemplo: `HighAmountRule` — DECLINE si `amountCents > 10_000_000`.

### 2. `combination`

Combina N sub-reglas inline (no reglas del catalogo global). Cada sub-regla puede ser threshold o boolean.

```
parameters:
  requireAll: bool        -- AND si true, OR si false
  subrules:
    - type: "boolean" | "threshold"
      field: string
      equals: bool        -- para boolean
      operator: string    -- para threshold
      value: any
```

Ejemplo: `NewDeviceYoungCustomer` — REVIEW si dispositivo nuevo AND cuenta < 30 dias.

### 3. `velocity`

Cuenta eventos en una ventana de tiempo deslizante para un grupo (customerId, deviceId, ip).

```
parameters:
  count:         int      -- maximo permitido
  windowMinutes: int      -- tamano de la ventana
  groupBy:       string   -- campo de agrupacion
```

Nota de implementacion: requiere acceso a un store de ventana (Redis sorted set por score=timestamp). El interprete no lee directamente Redis; lee el campo pre-computado en `FeatureSnapshot` (ver separacion de concerns en la seccion Performance).

### 4. `chargeback_history`

Especializado en threshold sobre campo de historial de chargebacks. Separado de `threshold` generico porque el campo viene de un store distinto (historial de 90d) y puede requerir TTL diferenciado en cache.

```
parameters:
  field:     string   -- "chargebackCount90d" | "chargebackCount30d"
  threshold: int
  operator:  ">=" | ">"
```

### 5. `international`

Evalua si el pais del comercio o del issuer esta en una lista de paises restringidos.

```
parameters:
  restrictedCountries: string[]   -- ISO 3166-1 alpha-2
  action:              RuleAction
```

### 6. `time_of_day`

Evalua si la transaccion ocurre dentro de una ventana horaria en determinados dias.

```
parameters:
  hoursFrom: int        -- hora inicio (UTC o local segun config)
  hoursTo:   int        -- hora fin (puede cruzar medianoche)
  days:      string[]   -- ["MONDAY"..."SUNDAY"]
```

Cruce de medianoche (ej. 22:00 a 06:00): el interprete trata la ventana como `hour >= hoursFrom OR hour < hoursTo`.

### 7. `merchant_category`

Evalua si el MCC de la transaccion esta en una lista de categorias de alto riesgo.

```
parameters:
  mccCodes: string[]   -- lista de MCC de 4 digitos
```

Lista configurable por Riesgo; evita hardcodear que "gambling es riesgoso" en el codigo.

### 8. `device_fingerprint`

Compara el fingerprint del dispositivo contra una denylist (inline o lookup en tabla).

```
parameters:
  denyList: string[]           -- fingerprints inline (uso en dev/test)
  lookup:   string | null      -- nombre de tabla/cache si es lookup dinamico
```

La denylist inline es util en tests y PoC. En produccion, `lookup` apunta a una tabla que el equipo de Riesgo mantiene separadamente.

### 9. `allowlist`

Override que permite a clientes de confianza bypassar todas las demas reglas.

```
parameters:
  customerIds: string[] | null  -- lista inline
  lookup:      string | null    -- nombre de tabla
  override:    bool             -- si true, cortocircuita la evaluacion
```

---

## Aggregation policy

Cuando multiples reglas se disparan sobre una misma transaccion, se necesita una politica para combinar los resultados.

### Opciones evaluadas

| Politica | Descripcion | Cuando conviene |
|---|---|---|
| First match wins | La primera regla que matchea gana; el orden importa | Reglas mutuamente excluyentes bien ordenadas |
| Worst case wins | DECLINE > REVIEW > FLAG > ALLOW | Alta sensibilidad al riesgo |
| Weighted score | Suma de weights; threshold determina decision | Cuando el riesgo es un espectro continuo |
| Override | Reglas allowlist anulan todo | Para clientes VIP o pruebas de aceptacion |

### Recomendacion: Worst case wins con allowlist override y timeout cap

**Decision**: `aggregation_policy: "worst_case_with_allowlist_override"`.

**Razonamiento**:

1. **Worst case** es el mas conservador para fraude: si alguna regla dice DECLINE, no queremos que un score bajo de otra regla diluya esa senal. En fraude, el costo de un falso negativo (transaccion fraudulenta que pasa) supera ampliamente el costo de un falso positivo (transaccion legitima rechazada).

2. **Allowlist override** cortocircuita la evaluacion para clientes de confianza verificados. Sin esto, un cliente VIP con historial limpio podria ser bloqueado por una regla de velocity durante un pico de compras legitimas.

3. **Timeout cap**: si la evaluacion supera `timeout_ms`, se devuelve `fallback_decision: REVIEW`. Esto evita que un lookup lento (eg. tabla de devices) bloquee el request. REVIEW en lugar de DECLINE porque el timeout puede ser transitorio (infraestructura, no riesgo real).

**Lo que se resigna**: no hay scoring continuo. Una transaccion que dispara tres reglas de FLAG no se distingue de una que dispara una sola. Si el equipo de Riesgo quiere scoring, se puede agregar `"weighted_score"` como politica alternativa sin cambiar la arquitectura del engine — es un parametro, no un cambio de codigo.

---

## Storage del config

### Opciones evaluadas

| Opcion | Pros | Contras |
|---|---|---|
| YAML en disco | Versionable en git, simple, legible por humanos | Requiere file watch; no observable out-of-the-box |
| ConfigMap k8s | Nativo al cluster; watch via API; GitOps con ArgoCD | Acoplado a k8s; no util en local |
| DB tabla | Flexible; admin UI natural | Latencia en reads; esquema extra; no readable por humanos directamente |
| Remote config service (AppConfig/LaunchDarkly/Unleash) | Audit incorporado; rollout gradual; observable | Dependencia externa; coste; over-engineering para PoC |

### Recomendacion: progresion en tres etapas

**Etapa 1 — PoC y desarrollo local**: YAML en disco.
- El archivo `rules.yaml` se carga al inicio y se re-lee con `java.nio.file.WatchService`.
- Git es el audit trail: `git log -- rules.yaml` muestra quien cambio que y cuando.
- Cero infraestructura adicional.

**Etapa 2 — Integracion en cluster**: ConfigMap k8s.
- El YAML se mantiene como fuente de verdad en el repo; ArgoCD o Flux lo sincroniza al ConfigMap.
- El pod monta el ConfigMap como volumen; el file watch funciona igual.
- Rollback = `git revert` + push; ArgoCD aplica en segundos.

**Etapa 3 — Produccion real**: AWS AppConfig (o equivalente).
- Audit trail nativo con quien aprobo el deploy.
- Rollout gradual (canary: 5% del trafico usa nueva version antes de full deploy).
- Rollback instantaneo desde consola sin git.
- Metricas de adoption integradas.

La razon de la progresion es **reversibilidad**: el contrato entre el interprete y el config es el schema YAML. Si ese schema es estable, cambiar el mecanismo de entrega (disco → ConfigMap → AppConfig) no requiere modificar el engine. Se puede migrar una etapa a la vez.

---

## Versionado y rollback

Cada `RuleConfig` cargado en memoria tiene:

```
hash:        sha256 del contenido canonico del YAML (keys ordenadas, whitespace normalizado)
deployed_at: timestamp de cuando se cargo
deployed_by: actor del cambio (del campo del archivo o del header del admin API)
```

Cada decision evaluada incluye `rules_version_hash` en el response y en el evento de auditoria. Esto permite:

1. **Reproducibilidad**: dado un `rules_version_hash`, recuperar el snapshot exacto del config del momento.
2. **Debugging diferido**: semanas despues, un analista puede decir "esta transaccion se evaluo con las reglas del hash X" y reconstruir exactamente por que fue DECLINE.

### Rollback mecanismo

- **Git-based (etapas 1 y 2)**: `git revert <commit>` + push. ArgoCD aplica el ConfigMap anterior. El pod detecta el cambio via file watch y recarga atomicamente.
- **AppConfig (etapa 3)**: rollback desde consola o CLI en < 30 segundos; no requiere git.

Invariante: nunca se borra una version anterior del config. Se puede archivar, pero la historia tiene que ser recuperable.

---

## Hot reload strategy

### Mecanismo

```
AtomicReference<RuleConfig> currentConfig = new AtomicReference<>(loadFromDisk());

// En un hilo de background:
WatchService watcher = FileSystems.getDefault().newWatchService();
path.register(watcher, ENTRY_MODIFY);
while (true) {
    WatchKey key = watcher.take();
    RuleConfig newConfig = loadAndValidate(configPath);
    currentConfig.compareAndSet(currentConfig.get(), newConfig);
}
```

### Trade-off: reload atomico vs drain

| Enfoque | Descripcion | Trade-off |
|---|---|---|
| Swap directo (AtomicReference) | La referencia se actualiza; requests nuevos ven nuevo config | Requests en vuelo terminan con la version que leyeron al inicio |
| Drain + swap | Se espera que todos los requests in-flight terminen antes de swapear | Pausa perceptible bajo carga alta (hasta ~300ms) |

**Recomendacion**: swap directo con `AtomicReference`. Cada request lee `currentConfig.get()` al inicio de la evaluacion y usa esa referencia hasta el final. Si el swap ocurre a mitad de un batch de requests, algunos usaran v1 y otros v2 — eso es aceptable porque:

1. La ventana de inconsistencia es de microsegundos (el tiempo de evaluacion de reglas, < 10ms).
2. Ambas versiones son validas y fueron deployadas intencionalmente por Riesgo.
3. La alternativa (drain) introduce latencia bajo carga, que es el peor momento para que ocurra un reload.

El audit trail registra `rules_version_hash` por decision, por lo que si se necesita saber que version evaluo una transaccion especifica, es trazable.

---

## Performance

### Arquitectura de evaluacion

El engine no ejecuta reglas directamente contra el request HTTP. El pipeline es:

```
RiskRequest
    |
    v
FeatureExtractor  --> FeatureSnapshot {
                          amountCents, newDevice, customerAgeDays,
                          chargebackCount90d, transactionCount10m,
                          merchantMcc, country, deviceFingerprint,
                          ...
                      }
    |
    v
RuleEngine.evaluate(FeatureSnapshot, RuleConfig)
    |
    v
RuleDecision { action, triggeredRules, rulesVersionHash, evalMs }
```

`FeatureExtractor` materializa todos los campos que las reglas pueden necesitar antes de que el engine empiece. Esto permite:

1. **Paralelismo**: features costosas (eg. consulta a historial de chargebacks) se resuelven en paralelo, no dentro del loop de reglas.
2. **Testabilidad**: el engine recibe un `FeatureSnapshot` serializable; los tests no necesitan mocks de DB.
3. **Indexado por campo**: el engine mantiene un indice `Map<String, List<Rule>>` — solo evalua las reglas que dependen de los campos presentes en el snapshot.

### Pre-compilacion

Cada regla se compila a un `Predicate<FeatureSnapshot>` al momento del load, no en cada evaluacion:

```java
interface CompiledRule {
    RuleAction evaluate(FeatureSnapshot snapshot);
}

CompiledRule compile(Rule rule) {
    return switch (rule.type()) {
        case THRESHOLD   -> new ThresholdPredicate(rule.parameters());
        case COMBINATION -> new CombinationPredicate(rule.parameters());
        case VELOCITY    -> new VelocityPredicate(rule.parameters());
        // ...
    };
}
```

### Targets de performance

| Metrica | Target |
|---|---|
| Evaluacion de 100 reglas | < 1ms p99 |
| Hot reload (swap AtomicReference) | < 100 microsegundos |
| FeatureExtraction (sin IO remoto) | < 5ms p99 |
| FeatureExtraction (con IO: chargeback store) | < 20ms p99 |

El presupuesto de 300ms p99 del request completo es dominado por IO (DB de transacciones, autorizacion de red). El engine de reglas debe ser negligible.

---

## Key Design Principle

> Las reglas de fraude no las decide IT — las decide Riesgo. Por eso el engine no es un set de classes Java, es un interprete de configuracion con audit trail. La diferencia entre una decision correcta y un escandalo regulatorio puede ser una regla cargada con error humano; ese error tiene que ser auditable, reversible y atribuible.

---

## ADR-001: Worst Case Wins con Allowlist Override como Aggregation Policy

### Status
Accepted

### Context
El sistema evalua multiples reglas contra cada transaccion y necesita una politica unica para combinar los resultados. Las opciones son: first match, worst case, weighted score, o un hibrido.

### Decision
Adoptar `worst_case_with_allowlist_override`: el peor resultado (DECLINE > REVIEW > FLAG > ALLOW) gana, excepto cuando una regla de tipo `allowlist` con `override: true` dispara, en cuyo caso el resultado es ALLOW independientemente de otras reglas.

### Consequences
Mas facil: la logica de aggregation es determinista y explicable. "Fue DECLINE porque la regla X lo dice" es una explicacion simple para Riesgo y para el regulador.

Mas dificil: no hay scoring continuo. Si el equipo quiere "riesgo 73/100", necesitan cambiar la politica a `weighted_score`, lo que implica calibrar weights. Eso se puede hacer como extension futura sin cambiar el motor.

---

## ADR-002: YAML en disco para PoC, progresion a ConfigMap y AppConfig

### Status
Accepted

### Context
Se necesita un mecanismo de storage para el config de reglas que sea versionable, auditable y que soporte hot reload.

### Decision
Tres etapas: YAML en disco (PoC), ConfigMap k8s (cluster), AWS AppConfig (prod). El schema YAML es estable entre etapas; solo cambia el mecanismo de entrega.

### Consequences
Mas facil: cada etapa es funcional por si sola; la migracion es incremental; el engine no cambia entre etapas.

Mas dificil: AppConfig tiene coste y dependencia externa. En prod hay que planificar el acceso offline (cache local) para casos de degradacion del servicio de config.

---

## Estado de implementacion

| Componente | Clase / Modulo | Estado | Tests |
|---|---|---|---|
| FraudRule interface | `pkg/risk-domain` — `rule/FraudRule.java` | DONE | — |
| RuleAction enum (severity ordering) | `rule/RuleAction.java` | DONE | RULE-01..04 |
| FeatureSnapshot (signal bag) | `engine/FeatureSnapshot.java` | DONE | — |
| ThresholdRule | `rule/threshold/ThresholdRule.java` | DONE | RULE-05..07 |
| CombinationRule | `rule/combination/CombinationRule.java` | DONE | RULE-08..10 |
| VelocityRule | `rule/velocity/VelocityRule.java` | DONE | RULE-11..13 |
| ChargebackHistoryRule | `rule/chargeback/ChargebackHistoryRule.java` | DONE | — |
| InternationalRule | `rule/international/InternationalRule.java` | DONE | — |
| TimeOfDayRule | `rule/timeofday/TimeOfDayRule.java` | DONE | — |
| MerchantCategoryRule | `rule/merchantcategory/MerchantCategoryRule.java` | DONE | — |
| AllowlistRule | `rule/allowlist/AllowlistRule.java` | DONE | RULE-14..16 |
| DeviceFingerprintRule | `rule/device/DeviceFingerprintRule.java` | DONE | — |
| CompiledRuleSet (pre-compilation) | `engine/CompiledRuleSet.java` | DONE | — |
| RuleEngineImpl (AtomicReference reload) | `engine/RuleEngineImpl.java` | DONE | ENG-01..18 |
| AggregationPolicy (worst-case + allowlist override) | `engine/AggregationPolicy.java` | DONE | ENG-05..09 |
| RulesConfigLoader (YAML + SHA-256) | `config/RulesConfigLoader.java` | DONE | CFG-01..06 |
| RulesConfigValidator (fail-all) | `config/RulesConfigValidator.java` | DONE | CFG-07..10 |
| RulesAuditTrail (bounded deque) | `audit/RulesAuditTrail.java` | DONE | AUD-01..05 |
| BlockingBarrier | `mode/BlockingBarrier.java` | DONE | MODE-01 |
| ShadowMode (virtual thread) | `mode/ShadowMode.java` | DONE | MODE-02 |
| CircuitMode (p99 sliding window) | `mode/CircuitMode.java` | DONE | MODE-03 |
| Admin endpoints (bare-javac PoC) | `poc/no-vertx-clean-engine` — `HttpController.java` | DONE | ATDD 12 |
| Admin endpoints (Vert.x PoC) | `poc/vertx-layer-as-pod-eventbus` — `HttpVerticle.java` | DONE | ATDD 12 |
| JMH Benchmark (1 vs 100 rules, hot reload) | `bench/inprocess-bench` — `RuleEngineBenchmark.java` | DONE | — |
| v1 rules.yaml example | `examples/rules-config/v1/rules.yaml` | DONE | — |
| v2 rules.yaml example | `examples/rules-config/v2/rules.yaml` | DONE | — |
| v3-broken rules.yaml example | `examples/rules-config/v3-broken/rules.yaml` | DONE | — |

**Total tests**: 57 (unit + integration). Todos pasan. BUILD SUCCESSFUL (119 tasks).

**Cobertura**: `./gradlew :pkg:risk-domain:jacocoTestReport` — objetivo >85% en `rule/` y `engine/`.
