# Risk Decision Platform — exploración técnica

Plataforma de práctica para discutir decisiones de riesgo/fraude en tiempo real: 150 TPS sostenidos, presupuesto p99 < 300ms, camino crítico sincrónico para la decisión y flujo asíncrono para auditoría, eventos, ML/downstream y observabilidad.

> No es un sistema productivo cerrado. Es una demo técnica curada para hablar de arquitectura, performance, trazabilidad, boundaries limpios, despliegue distribuido y trade-offs operativos.

## Estado verificado actual

Última pasada share-ready: **2026-05-07**. Detalle completo en [`STATUS.md`](STATUS.md).

| Check | Estado |
|---|---|
| Repo Git local | OK (`git init` aplicado) |
| Tooling core `./nx setup --verify` | OK; k3d/kustomize/mc/otel-cli/websocat quedan como opcionales de deep dive |
| Quick tests `./nx test --composite quick` | OK: unit + architecture en secuencia determinística |
| Consistency audit `./nx audit consistency` | OK: 91.9% con `--strict`, threshold 80% |
| Confidentiality `./nx audit confidentiality` | OK: blocklist no vacía, scan real |
| Scrub `./nx scrub` | OK: sin patrones obvios de secretos/PII |
| Vert.x local pods | OK vía `poc/vertx-risk-platform/scripts/run-local-pods.sh` + `smoke.sh` |
| Compose distribuido Vert.x | Usable para demo avanzada; healthcheck alineado a `/health` y wait aumentado |

## Qué demuestra

- **Clean Architecture / Hexagonal**: dominio desacoplado de runtime/framework/infra.
- **Tres estilos de ejecución**: engine bare-javac, monolito Vert.x e implementación distribuida layer-as-pod.
- **Camino crítico vs async**: decisión online separada de auditoría/eventos/consumers.
- **Trazabilidad**: correlationId, idempotencyKey, eventos versionados y decision trace.
- **Resiliencia**: timeouts, circuit breakers, fallbacks e idempotencia.
- **Infra local**: Postgres, Valkey, Redpanda, MinIO, ElasticMQ, Moto, OpenBao, OTEL/OpenObserve.
- **Testing y verificación**: ATDD, ArchUnit, smoke tests, JMH y auditorías propias.
- **SDKs y contratos**: clientes Java/TypeScript/Go y contratos de eventos.

## Quickstart recomendado para demo

```bash
./nx setup --verify
./nx test --composite quick
./nx audit consistency
./nx audit confidentiality
./nx scrub
```

Demo rápida del engine HTTP:

```bash
./nx run risk-engine
# En otra terminal:
curl -s -X POST http://localhost:8080/risk \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-demo","customerId":"cust-1","amountCents":200000,"correlationId":"demo-1","idempotencyKey":"demo-1","newDevice":true}' | jq .
```

Demo local de separación por pods/permisos:

```bash
poc/vertx-risk-platform/scripts/run-local-pods.sh
poc/vertx-risk-platform/scripts/smoke.sh
poc/vertx-risk-platform/scripts/stop-local-pods.sh
```

Ver guía paso a paso en [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md).

## Arquitecturas incluidas

| PoC | Propósito | Comando principal |
|---|---|---|
| `poc/java-risk-engine` | Core sin frameworks; latencia base y Clean Architecture pura | `./nx run risk-engine` |
| `poc/java-monolith` | Una JVM Vert.x con infraestructura real | `./nx run java-monolith` |
| `poc/java-vertx-distributed` | 4 JVMs: controller/usecase/repository/consumer | `./nx up vertx` |
| `poc/vertx-risk-platform` | Pods locales con scopes/tokens entre capas | `poc/vertx-risk-platform/scripts/run-local-pods.sh` |
| `poc/k8s-local` | Manifiestos y addons para discusión EKS/K8s local | `poc/k8s-local/scripts/up.sh` |

## Stack Java

El baseline ejecutable actual usa **Java 21 LTS** por compatibilidad de Gradle/JMH/Karate y tooling local. El repo conserva ADRs/reglas que discuten **Java 25 LTS** como objetivo técnico cuando el ecosistema soporte classfile 25 sin fricción. Esa diferencia está documentada como trade-off operativo, no como contradicción oculta.

## Documentación principal

- [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) — recorrido seguro para mostrar el repo.
- [`STATUS.md`](STATUS.md) — matriz empírica actual.
- [`docs/00-START-HERE.md`](docs/00-START-HERE.md) — onboarding extendido.
- [`docs/09-architecture-question-bank.md`](docs/09-architecture-question-bank.md) — banco de preguntas/respuestas de arquitectura.
- [`docs/36-technical-interview-positioning.md`](docs/36-technical-interview-positioning.md) — frase de posicionamiento para discusión técnica.
- [`docs/12-rendimiento-y-separacion.md`](docs/12-rendimiento-y-separacion.md) — performance y trade-offs.
- [`vault/02-Decisions/`](vault/02-Decisions/) — ADRs.
