# pkg/ — Shared Library Modules

Estructura de shared libraries para apps internas, equivalente al patron de pkg/ en un monorepo enterprise Go. Each module is a self-contained Gradle subproject published under `com.naranjax.poc`.

## Modules

| Module             | Package suffix          | Contents                                                       |
|--------------------|-------------------------|----------------------------------------------------------------|
| `pkg:errors`       | `.errors`               | `BusinessException`, `ErrorCode` enum                         |
| `pkg:config`       | `.config`               | `EnvConfig` — typed env-var reader, no DI required             |
| `pkg:resilience`   | `.resilience`           | `CircuitBreaker`, `Retry`, `Bulkhead`, `RateLimiter`          |
| `pkg:events`       | `.events`               | `DecisionEvent`, `EventEnvelope<T>`, `OutboxEvent`, `DecisionEventPublisher`, `OutboxRepository` |
| `pkg:kafka`        | `.kafka`                | `KafkaRecord<T>`, `KafkaIdempotencyStore`, `TraceHeaders`     |
| `pkg:observability`| `.observability`        | `CorrelationId`, `MdcUtils`, `OtelSpan`                       |
| `pkg:repositories` | `.repositories`         | `Repository<T,ID>`, `TransactionRunner`                        |
| `pkg:integration-audit` | `.integrationaudit` | `IntegrationRequestLog` record                            |
| `pkg:testing`      | `.testing`              | `Builders` — shared test-data factories (Phase 2)             |

## Adding a new pkg module

1. Create `pkg/<name>/build.gradle.kts` with `plugins { id("naranja.library-conventions") }`.
2. Add `"pkg:<name>"` to the `include(...)` block in `settings.gradle.kts`.
3. Create sources under `pkg/<name>/src/main/java/com/naranjax/poc/pkg/<name>/`.
4. Other modules declare a dependency with `implementation(project(":pkg:<name>"))`.

## Phase 2 migration note

Classes in this directory are **copies** of originals in `poc/java-risk-engine/`. Once Phase 2 migrates the application modules, the duplicates in the PoC will be removed and apps will depend on these modules directly.
