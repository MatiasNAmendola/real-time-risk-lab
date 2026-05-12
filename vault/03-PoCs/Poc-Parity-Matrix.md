---
title: PoC Parity Matrix — paridad lógica y performance entre PoCs Java
tags: [poc, parity, performance, architecture, vertx, java, benchmark]
created: 2026-05-12
source_archive: docs/13-paridad-logica-poc.md + docs/38-java-apps-architecture-performance-matrix.md (merge, migrado 2026-05-12)
---

# PoC Parity Matrix — paridad lógica y performance entre PoCs Java

**Actualizado el:** 2026-05-08 (PoCs Java principales)
**PoCs:** `poc/no-vertx-clean-engine/`, `poc/vertx-monolith-inprocess/`, `poc/vertx-layer-as-pod-http/`, `poc/vertx-layer-as-pod-eventbus/`, `poc/vertx-service-mesh-bounded-contexts/`

> Varias arquitecturas, mismo problema. no-vertx-clean-engine muestra Clean Arch sin frameworks. vertx-monolith-inprocess muestra el mismo dominio con stack moderno en un proceso. vertx-layer-as-pod-http muestra inter-pod vía HTTP + tokens. vertx-layer-as-pod-eventbus muestra controller/usecase/repository vía EventBus clustered y un consumer downstream por Kafka. Cada uno responde una pregunta arquitectónica distinta.

---

## Sección 1: Tabla comparativa de arquitectura (de docs/13)

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

## Sección 2: Matriz de paridad funcional (post-fix, 2026-05-07)

| Capability | no-vertx-clean-engine | vertx-layer-as-pod-eventbus | Status |
|---|---|---|---|
| HighAmountRule (named, versioned) | `HighAmountRule implements FraudRule` | `HighAmountRule implements FraudRule` en `shared/rules/` | OK — NEW |
| NewDeviceYoungCustomerRule | `newDevice && customerAgeDays < 30` | `NewDeviceYoungCustomerRule` en `shared/rules/` | OK — NEW |
| FeatureSnapshot raw signals | `{customerAgeDays, chargebackCount90d}` | `{customerAgeDays, chargebackCount90d, knownDevice, riskScore, country}` | OK — NEW |
| LatencyBudget end-to-end | `LatencyBudget` nanosecond deadline | `LatencyBudget(280ms)` en `EvaluateRiskVerticle`, gating ML scorer | OK — NEW |
| Circuit Breaker (around ML) | `CircuitBreaker` count-based | `SimpleCircuitBreaker(3 failures, 30s reset)` en `shared/resilience/` | OK — NEW |
| Idempotency store | `InMemoryDecisionIdempotencyStore` | `IdempotencyVerticle` (ConcurrentHashMap, putIfAbsent) en `repository-app` | OK — NEW (in-memory) |
| Named rules sealed interface | `FraudRule` sealed | `FraudRule` sealed en `shared/rules/` | OK — NEW |
| Decisiones APPROVE / REVIEW / DECLINE | Enum `Decision` | String factory methods en `RiskDecision` | Semánticamente equivalentes. Phase 2: enum compartido. |
| Outbox pattern | `InMemoryOutboxRepository` + `OutboxRelay` | `publishToKafka()` directo (intencional en PoC) | Deuda técnica intencional — Phase 2 |
| ML scorer frontera | Port `RiskModelScorer`, score 0-100 | Score pre-materializado en Postgres (0.0-1.0) | Diferencia arquitectural documentada |
| Correlation ID HTTP → negocio | `CorrelationId` value object | String via MDC + EventBus header + JSON body | Paritario en concepto |
| HTTP REST | `HttpController` puerto 8081 | `HttpVerticle` puerto 8080 | Paritario en función |

---

## Sección 3: Inventario y mapa mental de PoCs (de docs/38)

```text
SIN VERT.X
  no-vertx-clean-engine
    -> ¿cuánto cuesta la lógica pura?

CON VERT.X
  vertx-monolith-inprocess
    -> ¿qué aporta Vert.x sin distribuir?

  vertx-layer-as-pod-eventbus
    -> ¿qué pasa si separo layers por pods usando EventBus clustered?

  vertx-layer-as-pod-http
    -> ¿qué pasa si separo layers por pods usando HTTP + tokens?

  vertx-service-mesh-bounded-contexts
    -> ¿qué pasa si separo por servicios/bounded contexts reales?
```

### Inventario real de apps Java

| App | Path | Tipo | Puerto demo | Propósito |
|---|---|---|---:|---|
| `no-vertx-clean-engine` | `poc/no-vertx-clean-engine` | Java 21, Clean Architecture, sin framework HTTP externo | 8081 | Baseline de lógica pura y menor overhead. |
| `vertx-monolith-inprocess` | `poc/vertx-monolith-inprocess` | Vert.x single JVM | 8090 | Mostrar Vert.x sin costo de red entre capas. |
| `vertx-layer-as-pod-eventbus` | `poc/vertx-layer-as-pod-eventbus` | Vert.x + Hazelcast, 3 JVMs síncronos + 1 consumer async | 8080 | Separar controller/usecase/repository como layers aisladas. |
| `vertx-layer-as-pod-http` | `poc/vertx-layer-as-pod-http` | Vert.x, 3 pods HTTP | 8180 | Contrastar HTTP explícito/debuggable vs EventBus. |
| `vertx-service-mesh-bounded-contexts` | `poc/vertx-service-mesh-bounded-contexts` | Vert.x + Hazelcast, bounded contexts | 8090 | Service-to-service real en Vert.x. |

---

## Sección 4: Matriz arquitectónica comparativa (de docs/38)

| Dimensión | Bare Java | Vert.x monolith | Vert.x layer-as-pod | Vert.x HTTP pods | Service-to-service |
|---|---|---|---|---|---|
| Proceso/JVM | 1 | 1 | 4 | 3 | 4 |
| Unidad de separación | Paquetes Clean Arch | Verticles/capas | Capas como pods | Capas como pods HTTP | Bounded contexts |
| Hop entre capas | Método | EventBus local | EventBus clustered | HTTP | EventBus RPC/async |
| Serialización interna | No | Baja/local | Sí | Sí/JSON HTTP | Sí |
| Aislamiento de fallo | Bajo | Bajo | Medio/alto por capa | Medio/alto por pod | Alto por bounded context |
| Escalado independiente | No | No | Por capa | Por capa | Por servicio de negocio |
| Complejidad operativa | Baja | Media | Alta | Media/alta | Alta |
| Mejor uso demo | Latencia base | Vert.x sin red | Costo/beneficio de aislar capas | Transparencia/permisos HTTP | Microservicios reales |

---

## Sección 5: Matriz de performance (hipótesis arquitectónica de docs/38)

Esta tabla es **hipótesis arquitectónica**, no resultado final medido. Los valores finales deben salir de `./nx bench k6 competition ...`, JMH y runs documentados.

| Stack | Latencia esperada | Throughput esperado | Causa principal | Riesgo |
|---|---|---|---|---|
| Bare Java | Mejor p50/p99 para lógica pura | Alto para CPU/light I/O | Sin framework ni red interna | Menos parecido a producción distribuida. |
| Vert.x monolith | Muy cerca del bare en hot path I/O | Alto | Event loop + EventBus local, sin hops TCP | Blast radius de una JVM. |
| Vert.x EventBus clustered | Mayor que monolith | Alto si el I/O no bloquea | Serialización + TCP + cluster manager | Tuning EventBus/Hazelcast, opacidad de debugging. |
| Vert.x HTTP pods | Mayor que EventBus en general | Bueno, menor por overhead HTTP/JSON | HTTP parse/serialize por hop | Más simple de observar, pero más costo por request. |
| Service-to-service | Variable | Depende de fan-out y timeouts | RPC a fraud-rules/ML + audit async | Latencia compuesta y fallos parciales. |

Orden esperado de menor overhead en la ruta crítica:

```text
bare Java ~= Vert.x monolith
  < Vert.x EventBus clustered
  < Vert.x HTTP inter-pod
  < service-to-service con fan-out sync
```

Con una advertencia clave: si el scorer ML simulado consume 100–150 ms, ese dependency domina el p99 y puede ocultar diferencias de 1–20 ms entre stacks.

---

## Sección 6: Benchmark JMH in-process (números medidos, de docs/12)

**Hardware del host:** Apple M1 Pro, 10 núcleos, 16 GB

**JVM:** JDK 21.0.4 Temurin, `-Xms256m -Xmx512m --enable-preview`

| Metrica | In-process (bare-javac) | Distributed (Vert.x layer-as-pod) |
|---------|------------------------:|-----------------------------------:|
| p50 latencia | ~0.125 µs | (pending — run distributed bench) |
| p99 latencia | ~0.459 µs | (pending — run distributed bench) |
| p99.9 latencia | ~26 µs | (pending — run distributed bench) |
| Worst case (p100) | 160 ms | (pending — run distributed bench) |
| Throughput (single thread) | ~25 ops/s | (pending) |
| Throughput (32 vthreads, old runner) | ~1 528 req/s | ~800 req/s (est.) |
| RSS memoria | ~150 MB | ~600 MB total (5 x 120 MB) |
| Contenedores | 1 JVM | 5 JVMs |

Para reproducir:

```bash
cd bench
./gradlew -pl inprocess-bench -am clean package -q
bench/scripts/run-inprocess.sh -wi 1 -i 3
# Output en: out/bench/inprocess/latest.json
```

---

## Cómo contarlo en una discusión técnica

> "No hice cinco apps porque sí. Mantengo el mismo problema de decisión de riesgo y cambio la topología: no-Vert.x Java para latencia base, `vertx-monolith-inprocess` para event loop sin red, `vertx-layer-as-pod-eventbus` para layer-as-pod con EventBus clustered, `vertx-layer-as-pod-http` para HTTP+tokens entre pods, y vertx-service-mesh-bounded-contexts para bounded contexts reales dentro del stack Vert.x. Así puedo mostrar cuánto cuesta cada separación y cuándo vale la pena, sin afirmar que Vert.x imponga una topología de pods."

---

## Key Design Principle

> "Los dos PoCs resuelven el mismo problema con dos arquitecturas. La lógica de dominio está duplicada intencionalmente para que cada PoC sea autocontenido y muestre las decisiones estructurales de forma aislada: no-vertx-clean-engine demuestra domain modeling puro sin frameworks, y Vert.x demuestra distribución, trazado y protocolos de notificación. El siguiente paso es extraer ese dominio a un módulo compartido para que las dos apps sean exclusivamente wiring de adapters alrededor del mismo núcleo. Esa duplicación es deuda técnica intencional de demo, no de producción."

## Related

- [[In-Process-vs-Distributed]] — análisis conceptual del overhead de separación.
- [[Vertx-Layer-As-Pod-HTTP]] — PoC de separación con HTTP + tokens.
- [[no-vertx-clean-engine]] — PoC baseline sin frameworks.
- [[vertx-layer-as-pod-eventbus]] — PoC con EventBus clustered.
- [[Layer-as-Pod]] — concepto de layer-as-pod.
- [[Risk-Platform-Overview]]
