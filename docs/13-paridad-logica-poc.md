# Paridad de lógica de negocio entre PoCs Java

**Actualizado el:** 2026-05-07 (PoCs Java principales)
**PoCs:** `poc/no-vertx-clean-engine/` (bare-javac), `poc/vertx-monolith-inprocess/` (single-JVM), `poc/vertx-layer-as-pod-http/` (HTTP+tokens), `poc/vertx-layer-as-pod-eventbus/` (event bus)
**Alcance:** arquitectura comparativa — mismo dominio, cuatro formas de desplegarlo.

---

> Varias arquitecturas, mismo problema. no-vertx-clean-engine muestra Clean Arch sin frameworks. vertx-monolith-inprocess muestra el mismo dominio con stack moderno en un proceso. vertx-layer-as-pod-http muestra inter-pod vía HTTP + tokens. vertx-layer-as-pod-eventbus muestra controller/usecase/repository vía EventBus clustered y un consumer downstream por Kafka. Cada uno responde una pregunta arquitectónica distinta.

---

## 0. Tabla comparativa de arquitectura

| Capability | no-vertx-clean-engine | vertx-monolith-inprocess | vertx-layer-as-pod-http | vertx-layer-as-pod-eventbus |
|---|---|---|---|---|
| Frameworks externos | none | Vert.x + AWS SDK + JDBC | Vert.x | Vert.x + Hazelcast + AWS SDK + JDBC |
| Layer separation | in-process methods | in-process verticles | 3 pods HTTP | 3 pods clustered EventBus + 1 consumer Kafka |
| Inter-pod comm | n/a | EventBus local | HTTP | Hazelcast TCP binary para path síncrono; Kafka para consumer |
| Permission model | n/a | n/a | token-based custom | networks (`app-net`, `async-net`, `data-net`) + credentials/env por rol |
| Persistencia real | in-memory | Postgres + Valkey | in-memory | Postgres + Valkey |
| Cluster manager | n/a | n/a | none | Hazelcast |
| K8s manifests | no | no | yes (NetworkPolicy) | no |
| HTTP REST | OK | OK | OK | OK |
| SSE / WS / Webhooks | No | OK | No | OK |
| Kafka publish | No (in-mem) | OK | No (outbox in-mem) | OK |
| S3 / SQS / Secrets | No (NoOp) | OK | No | OK |
| Idempotency | in-memory | Lettuce/Valkey | in-memory | in-memory PoC |
| Blast radius | full | full | reduced (3 JVMs) | reduced (3 sync JVMs + async consumer JVM) |
| Scaling indep. layers | No | No | Yes | Yes |
| Port | 8081 | 8090 | 8180 (controller) | 8080 (controller) |
| Best for showing | Clean Arch sin frameworks | Latencia minima con stack moderno | HTTP-vs-event-bus + tokens-vs-networks | Distributed con isolation + scaling indep |

---

## 1. Matriz de paridad funcional (post-fix, 2026-05-07)

| Capability | no-vertx-clean-engine | vertx-layer-as-pod-eventbus | Status |
|---|---|---|---|
| HighAmountRule (named, versioned) | `HighAmountRule implements FraudRule` | `HighAmountRule implements FraudRule` en `shared/rules/` | OK — NEW |
| NewDeviceYoungCustomerRule | `newDevice && customerAgeDays < 30` | `NewDeviceYoungCustomerRule` en `shared/rules/`; `RiskRequest.newDevice` | OK — NEW |
| FeatureSnapshot raw signals | `{customerAgeDays, chargebackCount90d}` | `{customerAgeDays, chargebackCount90d, knownDevice, riskScore, country}` | OK — NEW |
| LatencyBudget end-to-end | `LatencyBudget` nanosecond deadline | `LatencyBudget(280ms)` en `EvaluateRiskVerticle`, gating ML scorer | OK — NEW |
| Circuit Breaker (around ML) | `CircuitBreaker` count-based | `SimpleCircuitBreaker(3 failures, 30s reset)` en `shared/resilience/` | OK — NEW |
| Idempotency store | `InMemoryDecisionIdempotencyStore` | `IdempotencyVerticle` (ConcurrentHashMap, putIfAbsent) en `repository-app` | OK — NEW (in-memory) |
| Named rules sealed interface | `FraudRule` sealed | `FraudRule` sealed en `shared/rules/`; permits HighAmountRule, NewDeviceYoungCustomerRule | OK — NEW |
| RuleEvaluation (triggered+reason+weight) | `RuleEvaluation` record | `RuleEvaluation` record en `shared/rules/` | OK — NEW |
| Decisiones APPROVE / REVIEW / DECLINE | Enum `Decision` | String factory methods en `RiskDecision` | Semánticamente equivalentes. Phase 2: enum compartido. |
| Outbox pattern | `InMemoryOutboxRepository` + `OutboxRelay` | `publishToKafka()` directo (publish-directo intencional en PoC) | Deuda técnica intencional — Phase 2 |
| ML scorer frontera | Port `RiskModelScorer`, score 0-100 | Score pre-materializado en Postgres (0.0-1.0); mismo resultado sin llamada externa | Diferencia arquitectural documentada, no gap de funcionalidad |
| Correlation ID HTTP → negocio | `CorrelationId` value object | String via MDC + EventBus header + JSON body | Paritario en concepto |
| Propagación OTel trace | Sin agente, solo stdout | Java agent + span attributes `risk.rule.*`, `risk.ml.score`, `risk.budget.remaining_ms`, `risk.fallback_applied`, `risk.cb.state` | Vertx tiene más trazabilidad |
| HTTP REST | `HttpController` puerto 8081 | `HttpVerticle` puerto 8080 | Paritario en función |
| WebSocket / SSE / Webhooks | Ausente | Presente en Vertx | OK por diseño del PoC |
| Kafka publish + consume | Outbox relay (stdout) / placeholder | `publishToKafka()` + `RiskDecisionConsumerVerticle` | OK por diseño |
| Persistencia real | In-memory | Postgres via `vertx-pg-client` + migration idempotente | OK por diseño |
| Métricas Micrometer | Ausente | Counters, Timer, Gauge | Vertx tiene más observabilidad |
| Tests ATDD | Smoke tests manuales | Suite Karate: 11 feature files (incluye `11_latency_budget.feature` nuevo) | Paritario en filosofía |

---

## 2. Gaps cerrados (2026-05-07)

Los seis gaps identificados en la auditoría original fueron implementados en `poc/vertx-layer-as-pod-eventbus/`:

| # | Gap | Implementación |
|---|---|---|
| 1 | HighAmountRule inline anónima | `shared/rules/HighAmountRule.java` — objeto nombrado, versionado (`v1`), con umbral configurable |
| 2 | NewDeviceYoungCustomerRule ausente | `shared/rules/NewDeviceYoungCustomerRule.java` + `RiskRequest.newDevice` + `FeatureSnapshot.customerAgeDays` |
| 3 | FeatureSnapshot sin señales crudas | `FeatureSnapshot` ampliado con `customerAgeDays`, `chargebackCount90d`, `knownDevice`; `DbBootstrap` migra la tabla idempotentemente |
| 4 | LatencyBudget ausente | `shared/resilience/LatencyBudget.java`; `EvaluateRiskVerticle` crea `LatencyBudget(280ms)` por request y lo usa para guardar la llamada ML |
| 5 | CircuitBreaker ausente | `shared/resilience/SimpleCircuitBreaker.java`; instancia `mlBreaker` en `EvaluateRiskVerticle` con threshold=3, reset=30s |
| 6 | Idempotency store sin implementar | `IdempotencyVerticle` (ConcurrentHashMap putIfAbsent) en `repository-app`; `EvaluateRiskVerticle` hace GET antes de evaluar y PUT después |

## 3. Análisis de gaps (original, para referencia)

### Gap 1: HighAmountRule — regla sin objeto en Vertx

**Estado actual:**  
- `no-vertx-clean-engine`: objeto `HighAmountRule implements FraudRule`, nombrado `"high-amount-v1"`, umbral `>= 50_000` cents, registrado en `DecisionTrace`.  
- `vertx-layer-as-pod-eventbus`: comparación `amountCents > 50_000` inline en `EvaluateRiskVerticle.applyPolicy()`, sin nombre, sin versión, sin trace entry.

**Por qué importa:**  
En producción una regla sin nombre no puede auditarse ni desactivarse feature-flag. This demonstrates the difference between domain modeling and scripting.

**Plan de unificación:**  
Extraer `FraudRule` + `HighAmountRule` a `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/rule/`. Ambos PoCs dependen del módulo compartido. `EvaluateRiskVerticle` instancia `RuleBasedDecisionPolicy` en lugar de su `applyPolicy()` inline.

**Esfuerzo:** bajo.

---

### Gap 2: NewDeviceYoungCustomerRule ausente en Vertx

**Estado actual:**  
- `no-vertx-clean-engine`: `NewDeviceYoungCustomerRule` evalúa `newDevice && customerAgeDays < 30`. `TransactionRiskRequest` tiene campo `newDevice`. `FeatureSnapshot` tiene `customerAgeDays`.  
- `vertx-layer-as-pod-eventbus`: `RiskRequest` no tiene `newDevice`. `FeatureSnapshot` no tiene `customerAgeDays`. La regla no existe.

**Por qué importa:**  
Esta regla es el ejemplo pedagógico principal de combinar una señal de request con una señal de feature store. Sin ella el PoC Vert.x no demuestra el patrón de feature enrichment en la capa de dominio.

**Plan de unificación:**  
Agregar `newDevice: boolean` a `RiskRequest` (shared). Migrar `FeatureSnapshot` shared para incluir `customerAgeDays`. Aplicar la regla compartida en `EvaluateRiskVerticle`. El repo Postgres puede retornar el campo desde la tabla `customer_features`.

**Esfuerzo:** medio (requiere schema change + migración en `DbBootstrap`).

---

### Gap 3: FeatureSnapshot — modelos incompatibles

**Estado actual:**  
- `no-vertx-clean-engine`: `{customerAgeDays: int, chargebackCount90d: int}` — señales crudas.  
- `vertx-layer-as-pod-eventbus`: `{customerId: String, riskScore: double, country: String}` — score pre-calculado, sin señales crudas, sin `chargebackCount90d`.

**Por qué importa:**  
El dominio necesita señales crudas para poder aplicar reglas deterministas (ej. `chargebackCount90d > 0` en `FallbackDecisionPolicy`). El score pre-calculado es útil como feature adicional pero no reemplaza las señales. Si Phase 2 comparte el módulo de dominio, necesita un `FeatureSnapshot` canónico.

**Plan de unificación:**  
Definir `FeatureSnapshot` canónico en `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` con el superconjunto de campos: `customerAgeDays`, `chargebackCount90d`, `riskScore`, `country`. El repo Postgres retorna todos los campos relevantes. El `InMemoryFeatureProvider` de bare-javac completa los nuevos campos.

**Esfuerzo:** medio.

---

### Gap 4: LatencyBudget ausente en Vertx

**Estado actual:**  
- `no-vertx-clean-engine`: `LatencyBudget` con deadline en nanosegundos. `EvaluateTransactionRiskService` verifica `budget.hasAtLeast(MIN_MODEL_BUDGET)` antes de invocar el scorer. El scorer recibe `budget.remaining()` como timeout.  
- `vertx-layer-as-pod-eventbus`: `TIMEOUT_MS = 10_000` fijo en `DeliveryOptions`. No hay concepto de cuánto presupuesto queda antes de llamar al scorer.

**Por qué importa:**  
Sin budget dinámico, el scorer puede consumir tiempo que no existe, causando que la respuesta HTTP supere el SLA aunque internamente haya tomado la decisión correcta. It is a key technical differentiator in technical-leadership-level design discussions.

**Plan de unificación:**  
Extraer `LatencyBudget` a `pkg/observability/` o `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/`. `EvaluateRiskVerticle` recibe el budget a partir del timestamp del mensaje de EventBus y lo pasa al scorer. Requiere cambiar la interfaz del verticle para aceptar un timeout de entrada.

**Esfuerzo:** medio.

---

### Gap 5: Circuit Breaker ausente en Vertx

**Estado actual:**  
- `no-vertx-clean-engine`: `CircuitBreaker` (count-based, openDuration configurable). Verificado antes de invocar scorer. `modelCircuitBreaker.failure()` en caso de excepción; `success()` en éxito.  
- `vertx-layer-as-pod-eventbus`: Sin CB. Si el scorer / repositorio Postgres empieza a fallar, todas las requests siguen intentando hasta timeout del EventBus (10s).

**Por qué importa:**  
The CB is the most frequently discussed resilience pattern in distributed systems design reviews. Sin él el PoC Vert.x no demuestra degradación controlada bajo fallo del dependency externo.

**Plan de unificación:**  
Definir `CircuitBreakerPort` como interfaz en `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/`. La implementación concreta queda en `pkg/resilience/src/main/java/io/riskplatform/poc/pkg/resilience/`. Ambos PoCs inyectan la implementación. Esto también resuelve una futura violación ArchUnit si se activa la regla de que domain no depende de infraestructura.

**Esfuerzo:** bajo (la implementación ya existe; falta el port y el wire en Vertx).

---

### Gap 6: Idempotency store — contrato definido, implementación ausente en Vertx

**Estado actual:**  
- `no-vertx-clean-engine`: `InMemoryDecisionIdempotencyStore` implementado y testado en `ArchitectureSmokeTest` (verifica que el segundo llamado retorna la misma instancia).  
- `vertx-layer-as-pod-eventbus`: `07_idempotency.feature` define el contrato Karate completo (mismo response para mismo `idempotencyKey`), pero `EvaluateRiskVerticle` no tiene ningún store. El test Karate actualmente no puede pasar de forma determinista.

**Por qué importa:**  
Idempotencia es un requisito de correctness, no de performance. En un sistema de pagos, procesar la misma transacción dos veces es un incidente.

**Plan de unificación:**  
Implementar `IdempotencyStore` en `EvaluateRiskVerticle` usando el `Idempotency-Key` header (ya expuesto en el ATDD). En Phase 2 con AWS wire, reemplazar por SetNX en Valkey/ElastiCache.

**Esfuerzo:** bajo (in-memory) / alto (Valkey en AWS).

---

### Gap 7: Outbox vs. publish-directo

**Estado actual:**  
- `no-vertx-clean-engine`: `InMemoryOutboxRepository` (append, pending, markPublished) + `OutboxRelay` (flush async). El evento no se pierde si el publisher falla porque queda en el store pendiente. El relay actualmente imprime a stdout (el Kafka wire no existe en este PoC).  
- `vertx-layer-as-pod-eventbus`: `publishToKafka()` directo al producer. Si Kafka no está disponible en el instante de la decisión, el evento se pierde silenciosamente (el `onFailure` solo loggea).

**Por qué importa:**  
El outbox garantiza exactly-once semántics a nivel de publicación. La publicación directa en Vertx es correcto para el PoC de demo pero no para producción.

**Plan de unificación:**  
En Phase 2 implementar outbox real con tabla `outbox_events` en Postgres + relay periódico que publica a Kafka y hace `UPDATE outbox SET published = true WHERE id = $1` en transacción. La interfaz `OutboxRepository` de no-vertx-clean-engine se puede llevar a `pkg/events/`.

**Esfuerzo:** alto (requiere Postgres transacción + relay + Kafka + test de fallo).

---

### Gap 8: ML scorer — frontera diferente

**Estado actual:**  
- `no-vertx-clean-engine`: `RiskModelScorer` es un port de salida. `FakeRiskModelScorer` calcula un score 0-100 a partir de señales crudas (amount, newDevice, chargebackCount90d) con latencia simulada y error aleatorio 15%.  
- `vertx-layer-as-pod-eventbus`: No hay port de scorer. El `riskScore` (double 0.0-1.0) llega pre-calculado desde la tabla `customer_features` en Postgres. La frontera de "qué calcula el modelo" está desplazada al data tier.

**Por qué importa:**  
En producción ambos modelos son válidos (scorer en línea vs. score pre-materializado). La diferencia es arquitectural and is worth explaining explicitly to avoid confusion sobre por qué los umbrales son distintos.

**Plan de unificación:**  
Documentar la diferencia como decisión intencional. Si se unifica el dominio, `RiskModelScorer` puede opcionalmente ser implementado como un adaptador que llama al campo `riskScore` del repositorio en lugar de un modelo en línea. Los umbrales de `ScoreDecisionPolicy` (0-100) deben mapearse al rango 0.0-1.0 del Vertx store.

**Esfuerzo:** bajo (es documentación + alineación de rangos).

---

## 3. Roadmap de unificación

| Step | Acción | Module destino | Quién consume | Phase |
|---|---|---|---|---|
| 1 | Extraer `FraudRule` interface + `HighAmountRule` + `NewDeviceYoungCustomerRule` | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/rule/` | no-vertx-clean-engine + vertx-layer-as-pod-eventbus | Phase 2 |
| 2 | Definir `FeatureSnapshot` canónico (superconjunto de campos de ambos PoCs) | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` | ambos | Phase 2 |
| 3 | Extraer `RuleBasedDecisionPolicy`, `ScoreDecisionPolicy`, `FallbackDecisionPolicy` | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` | ambos | Phase 2 |
| 4 | Extraer `LatencyBudget` | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` o `pkg/observability/` | ambos | Phase 2 |
| 5 | Definir `CircuitBreakerPort` (interface) en domain; mover impl a infra | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` + `pkg/resilience/src/main/java/io/riskplatform/poc/pkg/resilience/` | ambos (resuelve ArchUnit) | Phase 2 |
| 6 | Implementar `IdempotencyStore` en `EvaluateRiskVerticle` (in-memory con `ConcurrentHashMap`) | `usecase-app` | vertx-layer-as-pod-eventbus | Phase 2 |
| 7 | Agregar `newDevice` a `RiskRequest` shared + `customerAgeDays` a `FeatureSnapshot` shared + schema Postgres | `shared/`, `repository-app/`, `DbBootstrap.java` | vertx-layer-as-pod-eventbus | Phase 2 |
| 8 | Alinear umbrales `ScoreDecisionPolicy` entre PoCs (definir score range canónico) | `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` | ambos | Phase 2 |
| 9 | Outbox real con Postgres transacción + relay en `usecase-app` | `pkg/events/` + `usecase-app` | vertx-layer-as-pod-eventbus | Phase 2 + AWS wire |
| 10 | Activar ArchUnit en ambos PoCs usando el módulo compartido de dominio | módulo de test de cada PoC | ambos | Phase 2 |
| 11 | Reemplazar `InMemoryDecisionIdempotencyStore` de Vertx por SetNX en Valkey | `usecase-app` infra layer | vertx-layer-as-pod-eventbus | AWS wire (post Phase 2) |

---

## 4. Key Design Principle

> "Los dos PoCs resuelven el mismo problema con dos arquitecturas. La lógica de dominio está duplicada intencionalmente para que cada PoC sea autocontenido y muestre las decisiones estructurales de forma aislada: no-vertx-clean-engine demuestra domain modeling puro sin frameworks, y Vert.x demuestra distribución, trazado y protocolos de notificación. El siguiente paso es extraer ese dominio a un módulo compartido para que las dos apps sean exclusivamente wiring de adapters alrededor del mismo núcleo. Esa duplicación es deuda técnica intencional de demo, no de producción."

La paridad lógica está cerrada. La duplicación es deuda técnica intencional para la demo; la unificación a `pkg/risk-domain` viene en Phase 2. Pero ya no hay funcionalidad faltante en uno de los dos.

---

## 5. Lo que SI es paritario

- **Vocabulario de decisión:** APPROVE, REVIEW, DECLINE aparecen con los mismos nombres en ambos PoCs. Bare-javac usa `enum Decision`; Vertx usa String constants en factory methods. Un módulo shared puede exponer el enum y los dos consumen lo mismo.
- **Estructura de paquetes:** ambos siguen `controller → usecase → domain → infrastructure/repository`. Bare-javac lo hace con paquetes Java explícitos; Vertx lo hace con módulos Gradle separados que mapean a pods. El principio es idéntico.
- **Correlación ID end-to-end:** los dos propagan `correlationId` desde el header HTTP hasta el log estructurado del resultado. La mecánica difiere (value object vs. MDC String) pero el commitment de observabilidad es el mismo.
- **Records compartibles:** `TransactionId`, `CustomerId`, `Money`, `CorrelationId`, `IdempotencyKey` en no-vertx-clean-engine son records Java sin dependencias. Pueden moverse a `pkg/risk-domain/src/main/java/io/riskplatform/poc/risk/engine/` y ser consumidos directamente por Vertx shared con cero cambios de lógica.
- **Estilo de tests ATDD:** no-vertx-clean-engine tiene smoke tests con asserts explícitos que validan behavior desde afuera (idempotencia, trace, HTTP). Vertx tiene una suite Karate completa con 10 feature files. Ambos validan el comportamiento observable, no la implementación interna. La diferencia es el framework, no la filosofia.
- **Fake ML scorer con comportamiento no trivial:** los dos implementan un scorer que produce variabilidad (no-vertx-clean-engine: latencia aleatoria + error aleatorio + score calculado; Vertx: score pre-materializado en DB con valor fijo por customer). Ninguno hardcodea siempre el mismo resultado.
- **Separación de responsabilidades controller / usecase / repository:** en ninguno de los dos el controller toma decisiones de negocio, y el repositorio no conoce la policy de decisión. La separación está respetada en ambos.
