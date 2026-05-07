---
adr: "0021"
title: Testcontainers para Integration Tests Instead de Full docker-compose E2E
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/infrastructure]
---

# ADR-0021: Testcontainers para Integration Tests, Not Full docker-compose E2E

## Estado

Aceptado el 2026-05-07.

## Contexto

`tests/integration/` contains tests que require real infrastructure: un running Postgres instance, un running Kafka (or Redpanda) broker, un running SQS (ElasticMQ). These tests verify que la `pkg/*` repository implementations, Kafka producers, y SQS clients work contra real APIs — no mocks.

Two strategies exist: start la required services antes de la test run (docker compose up, then run tests, then docker compose down), o use Testcontainers un start containers en demand dentro de la JVM test lifecycle.

The ATDD suites en la Vert.x PoC (`poc/java-vertx-distributed/atdd-tests/`) already use docker compose because Karate needs la full application stack running. `tests/integration/` has un different concern: it tests `pkg/*` library code, no application behavior.

## Decisión

Use Testcontainers en `tests/integration/` para todos infrastructure dependencies. Containers start en `@BeforeAll`, run tests, y stop en `@AfterAll`. `pkg/testing/` provides shared `IntegrationTestSupport` base class con pre-configured Postgres, Redpanda, y ElasticMQ containers. Tests en `tests/integration/` extend `IntegrationTestSupport`.

ATDD tests en la Vert.x PoC continue un use docker compose because they test la full application stack, no library code. La two strategies coexist en different test layers.

## Alternativas consideradas

### Opción A: Testcontainers en JVM test lifecycle (elegida)
- **Ventajas**: No manual `docker compose up` required antes de running integration tests; containers son isolated per test class — no state leakage entre test classes; Testcontainers handles port assignment (avoids port conflicts en CI); container lifecycle es tied un test lifecycle — no orphaned containers if tests abort; reproducible regardless de local environment state.
- **Desventajas**: Container startup adds 5-15 seconds per test class un CI time; requires Docker daemon accessible desde la test JVM; Testcontainers pulls images en first run (network required); container reuse a través de test classes requires opt-in configuration (`reuse = true`).
- **Por qué se eligió**: For `pkg/*` library integration tests, la isolation y reproducibility benefits outweigh la startup cost. La alternative (docker compose up antes de mvn test) requires external orchestration que creates CI configuration complexity.

### Opción B: docker-compose para todos integration tests
- **Ventajas**: Services start once y son reused a través de todos test classes — faster total test time; same compose file como la application PoC — no duplication.
- **Desventajas**: Requires explicit docker compose up/down en CI pipeline; tests son no self-contained — they require external state setup; parallel test execution en different CI runners can conflict en fixed ports; compose file must be maintained en sync con Testcontainers configuration.
- **Por qué no**: La CI complexity (setup/teardown steps, port conflicts) es no justified para library integration tests. ATDD tests already use compose para application-level testing; library tests need un different level de isolation.

### Opción C: In-memory fakes (H2 para Postgres, in-memory Kafka mock)
- **Ventajas**: No Docker required; very fast startup; works en todos CI environments.
- **Desventajas**: H2 SQL dialect differs desde Postgres — algunos Postgres-specific features (JSONB, `ON CONFLICT`, window functions) son no supported; in-memory Kafka mocks (`EmbeddedKafka`) don't exercise real Kafka client configurations; la tests would be testing behavior contra mocks que may diverge desde production behavior.
- **Por qué no**: `pkg/repositories/` uses Postgres-specific SQL. Testing contra H2 would no catch Postgres-specific bugs. La value de integration tests es testing contra real APIs.

### Opción D: External persistent services (always-on dev environment)
- **Ventajas**: Fast test execution (no container startup); real service behavior.
- **Desventajas**: Tests depend en un external environment being available y clean; cross-developer state conflicts; CI requires access un la shared environment; no reproducible en un laptop sin VPN.
- **Por qué no**: Reproducibility es non-negotiable para CI. External shared services fail la reproducibility constraint.

## Consecuencias

### Positivo
- `./gradlew :tests:integration:test` es self-contained — no pre-steps required.
- `pkg/testing/IntegrationTestSupport` es reusable: new integration test classes extend it y get todos infrastructure containers para free.
- Container reuse (`reuse = true` en Testcontainers) reduces startup cost para desarrollo local runs.

### Negativo
- First run en un fresh Docker environment pulls 3 container images (~500MB total).
- Test class startup es 5-10 seconds slower than in-memory fakes.
- Tests require Docker daemon — cannot run en environments sin Docker (e.g., algunos corporate locked-down environments).

### Mitigaciones
- Testcontainers container reuse es enabled por default para local runs; CI does no reuse containers un maintain isolation.
- Images son cached en la CI layer cache después de la first pull.

## Validación

- `./gradlew :pkg:testing:test` runs y passes con Testcontainers containers started.
- `tests/integration/README.md` documents la Docker requirement.
- `IntegrationTestSupport` base class es used por a least 3 integration test classes.

## Relacionado

- [[0021-testcontainers-integration]] (self)
- [[0020-pkg-shared-modules]]
- [[0006-atdd-karate-cucumber]]
- [[0005-aws-mocks-stack]]

## Referencias

- Testcontainers Java: https://java.testcontainers.org/
