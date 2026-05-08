# Real-Time Risk Lab — exploración técnica

Plataforma de práctica para discutir decisiones de riesgo/fraude en tiempo real: 150 TPS sostenidos, presupuesto p99 < 300ms, camino crítico sincrónico para la decisión y flujo asíncrono para auditoría, eventos, ML/downstream y observabilidad.

> Te comparto una exploración técnica curada para discutir arquitectura de decisiones de riesgo en tiempo real. No intenta ser producción cerrada, sino una demo conversacional para hablar de trade-offs: Clean Architecture, boundaries, performance, trazabilidad, evaluación sincrónica, eventos asíncronos, permisos entre componentes, benchmarks y simulación local de despliegue distribuido.

## Alcance y licencia

- **No es producción**: es una exploración técnica reproducible para discusiones técnicas y reviews de arquitectura. Los mocks locales, credenciales de demo y manifiestos k8s existen solo para discusión y laboratorio.
- **No incluye licencia OSS**: salvo que se agregue un `LICENSE`, el código queda visible para lectura/review pero no concede permisos explícitos de copia, modificación o redistribución.
- **Stack de terceros**: MinIO/OpenBao/Redpanda y el resto del tooling se usan como servicios/dev dependencies; ver reglas internas de licencia en [`.ai/primitives/rules/licensing.md`](.ai/primitives/rules/licensing.md).

## Estado verificado actual

Última pasada share-ready: **2026-05-07**. Detalle completo en [`STATUS.md`](STATUS.md).

| Check | Estado |
|---|---|
| Repo Git local | OK (`git init` aplicado) |
| Tooling core `./nx setup --verify` | OK; k3d/kustomize/mc/otel-cli/websocat quedan como opcionales de deep dive |
| Quick check `./nx test --composite quick` | OK: guardrail live sin Gradle/JUnit; source boundaries + aviso de artefactos |
| Consistency audit `./nx audit consistency` | OK: 91.9% con `--strict`, threshold 80% |
| Confidentiality `./nx audit confidentiality` | OK: blocklist no vacía, scan real |
| Scrub `./nx scrub` | OK: sin patrones obvios de secretos/PII |
| Vert.x local pods | OK vía `poc/vertx-layer-as-pod-http/scripts/run-local-pods.sh` + `smoke.sh` |
| Benchmark in-process `./nx bench inproc` | OK; usar como evidencia de latencia base del core |
| Compose distribuido Vert.x | Modo avanzado; no es el demo principal |

## Qué demuestra

- **Clean Architecture / Hexagonal**: dominio desacoplado de runtime/framework/infra.
- **Estilos de ejecución comparables**: engine sin framework, monolito Vert.x, layer-as-pod HTTP/EventBus, service-to-service y k8s local.
- **Camino crítico vs async**: decisión online separada de auditoría/eventos/consumers.
- **Trazabilidad**: correlationId, idempotencyKey, eventos versionados y decision trace.
- **Resiliencia**: timeouts, circuit breakers, fallbacks e idempotencia.
- **Infra local**: Postgres, Valkey, Redpanda, MinIO, ElasticMQ, Moto, OpenBao, OTEL/OpenObserve.
- **Testing y verificación**: ATDD, ArchUnit, smoke tests, JMH y auditorías propias.
- **SDKs y contratos**: clientes Java/TypeScript/Go y contratos de eventos.

## Quickstart recomendado para demo

```bash
./nx setup --verify
./nx build
./nx test --composite quick
./nx audit consistency
./nx audit confidentiality
./nx scrub
```

Demo rápida del engine HTTP:

```bash
./nx run no-vertx-clean-engine
# En otra terminal:
curl -s -X POST http://localhost:8080/risk \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-demo","customerId":"cust-1","amountCents":200000,"correlationId":"demo-1","idempotencyKey":"demo-1","newDevice":true}' | jq .
```

Verificación full/CI opcional — no usar como comando principal live:

```bash
./nx build --legacy-clean -x test
# equivalente directo: ./gradlew clean build -x test
```

Benchmark principal:

```bash
./nx bench inproc
```

Checklist pre-publicación:

```bash
git status --short
cli/clean-ignored.sh
cli/clean-ignored.sh --force
./nx audit confidentiality
./nx scrub
rg -n -i "secret|token|password|api[_-]?key|private[_-]?key|aws|openai|slack|credential" .
```

Antes de publicar, revisar manualmente cualquier hit del último comando: muchas apariciones son documentación o mocks de desarrollo, pero no deben quedar tokens/credenciales reales ni artefactos internos de sesión.

Demo local de separación por pods/permisos:

```bash
poc/vertx-layer-as-pod-http/scripts/run-local-pods.sh
poc/vertx-layer-as-pod-http/scripts/smoke.sh
poc/vertx-layer-as-pod-http/scripts/stop-local-pods.sh
```

Ver guía paso a paso en [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md).

## Arquitecturas incluidas

La nomenclatura del repo está pensada para distinguir rápido **sin Vert.x vs con Vert.x** y qué topología demuestra cada PoC:

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

| PoC | Propósito | Comando principal |
|---|---|---|
| `poc/no-vertx-clean-engine` | Sin Vert.x: core sin frameworks; latencia base y Clean Architecture pura | `./nx run no-vertx-clean-engine` |
| `poc/vertx-monolith-inprocess` | Con Vert.x: una JVM, EventBus local, sin hops de red entre capas | `./nx run vertx-monolith-inprocess` |
| `poc/vertx-layer-as-pod-eventbus` | Con Vert.x: controller/usecase/repository como JVMs separadas vía clustered EventBus; consumer async por Kafka | `./nx up vertx-layer-as-pod-eventbus` |
| `poc/vertx-layer-as-pod-http` | Con Vert.x: pods/capas comunicados por HTTP explícito + tokens | `./nx up vertx-layer-as-pod-http` |
| `poc/vertx-service-mesh-bounded-contexts` | Con Vert.x: bounded contexts reales vía EventBus RPC/async | `poc/vertx-service-mesh-bounded-contexts/scripts/up.sh` |
| `poc/k8s-local` | Manifiestos y addons para discusión EKS/K8s local | `poc/k8s-local/scripts/up.sh` |

## Stack Java

El baseline ejecutable actual usa **Java 21 LTS** por compatibilidad de Gradle/JMH/Karate y tooling local. El repo conserva ADRs/reglas que discuten **Java 25 LTS** como objetivo técnico cuando el ecosistema soporte classfile 25 sin fricción. Esa diferencia está documentada como trade-off operativo, no como contradicción oculta.

## Utilidades CLI

```bash
# Ver qué archivos ignorados se eliminarían
cli/clean-ignored.sh

# Eliminar archivos/directorios ignorados no trackeados
cli/clean-ignored.sh --force
```

Ver [`cli/README.md`](cli/README.md) para el modo seguro, `--include-tracked` y notas de pre-share cleanup.

## Documentación principal

- [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) — recorrido seguro para mostrar el repo.
- [`STATUS.md`](STATUS.md) — matriz empírica actual.
- [`docs/00-START-HERE.md`](docs/00-START-HERE.md) — onboarding extendido.
- [`docs/09-architecture-question-bank.md`](docs/09-architecture-question-bank.md) — banco de preguntas/respuestas de arquitectura.
- [`docs/36-technical-positioning.md`](docs/36-technical-positioning.md) — frase de posicionamiento para discusión técnica.
- [`docs/12-rendimiento-y-separacion.md`](docs/12-rendimiento-y-separacion.md) — performance y trade-offs.
- [`vault/02-Decisions/`](vault/02-Decisions/) — ADRs.
