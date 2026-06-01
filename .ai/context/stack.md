# Stack — Versiones exactas

## Lenguajes y build

| Componente | Version | Notas |
|---|---|---|
| Java | 21 LTS baseline | `--release 21` por compatibilidad de tooling; Java 25 LTS queda como objetivo documentado. |
| Gradle | 3.9.x | `./gradlew --version` |
| Go | 1.22+ | Para `cli/risk-smoke/` |
| Python | >=3.11 | Gestionado con uv (`pyproject.toml`, `uv.lock`) |
| Bun | >=1.3 | Package manager/runtime obligatorio para JS/TypeScript; `ignoreScripts=true` |
| Bash | 5.x | Scripts de setup |

## Frameworks Java

| Componente | Version | Artifact |
|---|---|---|
| Vert.x stack | 5.0.12 | `io.vertx:vertx-stack-depchain:5.0.12` |
| Vert.x Core | 5.0.12 | `io.vertx:vertx-core` |
| Vert.x Web | 5.0.12 | `io.vertx:vertx-web` |
| Vert.x Kafka Client | 5.0.12 | `io.vertx:vertx-kafka-client` |
| Vert.x Redis Client | 5.0.12 | `io.vertx:vertx-redis-client` |
| Vert.x PgClient | 5.0.12 | `io.vertx:vertx-pg-client` |
| Vert.x Circuit Breaker | 5.0.12 | `io.vertx:vertx-circuit-breaker` |
| Vert.x OpenAPI | 5.0.12 | `io.vertx:vertx-openapi` |
| Hazelcast Cluster Manager | compatible Vert.x 5 | `io.vertx:vertx-hazelcast` |
| Jackson | 2.17.1 | `com.fasterxml.jackson.core:jackson-databind` |
| PostgreSQL JDBC | 42.7.3 | `org.postgresql:postgresql` |

## Testing Java

| Componente | Version | Notas |
|---|---|---|
| JUnit 5 | 5.11+ | `junit-jupiter` |
| Mockito | 5.x | Para unit tests |
| Karate | 1.5+ | `com.intuit.karate:karate-junit5` |
| Cucumber-JVM | 7.x | `io.cucumber:cucumber-java`, `cucumber-junit-platform-engine` |
| JaCoCo | 0.8.12 | Coverage |
| Testcontainers | 1.19+ | Para tests de integracion con DB real |

## Infraestructura local (Docker)

| Componente | Version / Image | Puerto default |
|---|---|---|
| Postgres | 18.4-alpine | 5432 |
| Valkey | 9.1.0-alpine | 6379 |
| Tansu | 0.6.0 | 9092 (Kafka, single listener) — ADR-0043 |
| OpenObserve | latest | 5080 |
| otelcol-contrib | 0.153.0 | 4317 (gRPC), 4318 (HTTP) |
| Moto (AWS mock) | latest | 5000 |
| MinIO | latest | 9000, 9001 (Console) |
| ElasticMQ | latest | 9324 |
| OpenBao | latest | 8200 |


## TypeScript / NestJS PoC

| Componente | Version | Notas |
|---|---|---|
| NestJS | 11.1.24 | `@nestjs/common`, `@nestjs/core`, `@nestjs/platform-express` |
| Hono | 4.12.23 | Adapter HTTP minimalista en `poc/hono-distributed-transactions` |
| @hono/node-server | 2.0.4 | Server local para Hono |
| @nestjs/cqrs | 11.0.3 | CQRS/EventBus para PoC transaccional |
| TypeScript | 6.0.3 | `ignoreDeprecations=6.0`, build con `tsc` |
| Bun | 1.3.14 verificado localmente | Package manager/runtime; lifecycle scripts bloqueados |
| TigerBeetle Node | 0.17.4 | Adapter opcional de ledger en `poc/nestjs-distributed-transactions` |
| BullMQ | 5.77.7 | Cola EDA sobre Valkey para mensajes de dominio |
| ioredis | 5.11.0 | Cliente Redis-compatible para Valkey |
| OpenTelemetry JS | 0.218.0 / API 1.9.1 | Export OTLP HTTP para traces/metrics |

## Kubernetes / Helm

| Componente | Version | Chart |
|---|---|---|
| k3d | v5.6+ | — |
| OrbStack | latest | — |
| Helm | 3.14+ | — |
| kubectl | 1.29+ | — |
| ArgoCD | 9.2.4 | `argo/argo-cd` |
| Argo Rollouts | 2.40.5 | `argo/argo-rollouts` |
| kube-prometheus-stack | 80.11.0 | `prometheus-community/kube-prometheus-stack` |
| External Secrets Operator | 1.2.1 | `external-secrets/external-secrets` |
| Tansu raw manifests | `addons/50-tansu.yaml` | `ghcr.io/tansu-io/tansu:0.6.0` |
| OpenObserve Helm | — | custom o standalone |

## Observabilidad

| Componente | Version | Notas |
|---|---|---|
| OpenTelemetry Java Agent | 2.x | javaagent attachment |
| OpenTelemetry API | 1.44.0 | `io.opentelemetry:opentelemetry-api` |
| OpenTelemetry SDK | via agent | No agregar directamente |

## Go CLI

| Componente | Version | Notas |
|---|---|---|
| Bubble Tea | latest | `github.com/charmbracelet/bubbletea` |
| Lip Gloss | latest | `github.com/charmbracelet/lipgloss` |
| Bubbles | latest | `github.com/charmbracelet/bubbles` |

## Verificar versiones locales

```bash
java -version           # 25.x
./gradlew --version            # 3.9.x
go version              # 1.22+
uv --version            # Python tooling
bun --version           # JS/TS tooling
docker version          # cualquier version reciente
kubectl version         # 1.29+
helm version            # 3.14+
k3d version             # 5.6+
```

## Workflows curados

- `.ai/primitives/workflows/technical-practice-checklist.md` — checklist previo a compartir la exploración técnica.
