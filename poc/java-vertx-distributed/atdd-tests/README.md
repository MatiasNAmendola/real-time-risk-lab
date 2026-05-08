# atdd-tests

Acceptance-Test-Driven suite for the `java-vertx-distributed` PoC.
Implemented with **Karate 1.5** orchestrated by **JUnit 5**, with **JaCoCo 0.8.12** for coverage.

---

## Prerequisites

- Java 21 LTS (`--release 21`); Java 25 queda como objetivo documentado
- Gradle 3.9+
- `docker compose up` running (all three Vert.x services + Kafka + PostgreSQL + OpenObserve)

---

## Running

```bash
# From the repo root — all features
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd

# With coverage report (runs verify phase, opens HTML report)
./scripts/atdd-coverage.sh

# Focused runner (REST only)
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd --tests io.riskplatform.atdd.runners.RestRunner

# CI environment (uses internal Docker DNS names)
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd -Dkarate.env=ci
```

---

## Module Structure

```
atdd-tests/
├── build.gradle.kts
├── README.md
└── src/test/
    ├── java/com/riskplatform/atdd/
    │   ├── RiskAtddSuite.java           # @KarateTest entry point
    │   ├── runners/
    │   │   ├── RestRunner.java          # 01, 02, 07, 08, 09
    │   │   ├── SseRunner.java           # 03
    │   │   ├── WebsocketRunner.java     # 04
    │   │   ├── WebhookRunner.java       # 05
    │   │   └── KafkaRunner.java         # 06
    │   └── support/
    │       ├── KafkaSteps.java          # Ephemeral consumer (Java interop)
    │       ├── WebhookListener.java     # Local HTTP server for callbacks
    │       └── TraceFinder.java         # Queries OpenObserve by traceId
    └── resources/
        ├── karate-config.js             # baseUrl, kafkaBroker, openObserveUrl
        ├── logback-test.xml
        └── features/
            ├── 01_health.feature
            ├── 02_rest_decision.feature
            ├── 03_sse_stream.feature
            ├── 04_websocket_bidi.feature
            ├── 05_webhook_callback.feature
            ├── 06_kafka_publish.feature
            ├── 07_idempotency.feature
            ├── 08_fallback_ml.feature   (@ignore — see file for enablement instructions)
            ├── 09_decline_threshold.feature
            └── 10_otel_trace_e2e.feature
```

---

## Coverage Matrix

| Feature | Adapters covered | Use cases covered | Resilience patterns tested |
|---|---|---|---|
| 01_health | Health controller | — | — |
| 02_rest_decision | HTTP controller, EventBus, FeatureRepo | EvaluateRisk | timeout (connect/read) |
| 03_sse_stream | HTTP controller, SSE handler | — | stream back-pressure (@wip) |
| 04_websocket_bidi | WS handler, EventBus | EvaluateRisk | real-time bidi |
| 05_webhook_callback | HTTP controller, WebhookRegistry, WebhookDispatcher | EvaluateRisk + Notify | retry + DLQ implicit |
| 06_kafka_publish | KafkaProducer, Outbox | EvaluateRisk + Publish | outbox pattern |
| 07_idempotency | IdempotencyStore | EvaluateRisk | idempotency |
| 08_fallback_ml | MlScorer (timeout sim), Rules engine | EvaluateRisk fallback path | circuit breaker + fallback |
| 09_decline_threshold | HTTP controller, policy engine | EvaluateRisk | boundary conditions |
| 10_otel_trace_e2e | ALL adapters | ALL use cases | observability + trace propagation |

---

## JaCoCo Coverage Notes

### Cross-module coverage (TCP server pattern — fully wired)

The ATDD suite measures real coverage of the four Vert.x services by attaching the JaCoCo agent to each JVM in TCP server mode.

#### How it works

Each service container runs with:
```
-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port=6300,append=true,includes=io.riskplatform.*
```

The agent opens a TCP socket on port 6300 inside the container. After all Karate tests finish, the Gradle `post-integration-test` phase connects to each service from the host and dumps the accumulated coverage data:

| Service | Container port | Host port |
|---|---|---|
| controller-app | 6300 | 6301 |
| usecase-app | 6300 | 6302 |
| repository-app | 6300 | 6303 |
| consumer-app | 6300 | 6304 |

Gradle JaCoCo tasks write execution data under `atdd-tests/build/jacoco/`. The merged report is generated at `atdd-tests/build/reports/jacoco/merged/` using production `build/classes/java/main` from sibling modules.

#### Running end-to-end coverage

```bash
# Fetch agent jar (idempotent)
./scripts/fetch-jacoco-agent.sh

# Start the stack (builds images with JaCoCo agent baked in)
./scripts/up.sh

# Run tests + collect coverage
./scripts/atdd-coverage.sh

# The aggregated report is opened automatically.
# To view later:
open atdd-tests/build/reports/jacoco/merged/index.html
```

The structured run report (including a per-package coverage table) is written to:
```
out/atdd-karate/latest/summary.md
out/atdd-karate/latest/coverage/
```

#### Limitations

- Coverage reflects only code paths exercised by the Karate scenarios. Code paths not touched by any test scenario show 0% coverage regardless of unit test results.
- The JaCoCo TCP dump requires the services to be running with the agent attached. Running `./gradlew -pl atdd-tests verify` without compose produces a `Connection refused` error on port 6301 during the dump phase — this is expected behaviour when no stack is running.
- The JaCoCo agent instruments bytecode at load time. Framework internals (Vert.x bootstrap, Hazelcast cluster manager, OpenTelemetry SDK) are excluded via the `includes=io.riskplatform.*` filter.
- `append=true` means multiple test runs accumulate coverage data. Restart containers to reset.

---

## Karate Version Notes

This module uses **Karate 1.5.0**. The `@KarateTest` annotation is the JUnit 5 integration mechanism.

If upgrading to Karate 2.x (when released as GA), check:
- `@KarateTest` annotation package may change
- `karate.webSocket()` API stability
- `karate.listen()` / `karate.signal()` async patterns

---

## Reports

`./scripts/atdd.sh` automatically generates a structured report after each run (pass or fail).

```
out/atdd-karate/
├── latest -> 2026-05-07T15-45-00/       # symlink to most recent run
└── 2026-05-07T15-45-00/
    ├── summary.md                        # table: feature / scenarios / status
    ├── summary.txt                       # grep-friendly plain text
    ├── full.log                          # copy of karate.log (DEBUG level)
    ├── meta.json                         # run metadata
    ├── features/
    │   ├── 01-health-check.md            # per-feature detail with step table
    │   └── ...
    └── coverage/                         # JaCoCo HTML report (if ./gradlew test jacocoTestReport ran)
        └── index.html
```

Quick read:
```bash
cat out/atdd-karate/latest/summary.md
cat out/atdd-karate/latest/features/01-health-check.md
```

The full Karate debug log (all HTTP requests, response bodies, match results) is written to `build/karate-reports/karate.log` during the run and copied to `out/atdd-karate/<timestamp>/full.log` by the report script.

---

## Failure Modes

| Situation | Behaviour |
|---|---|
| Services not running | `atdd.sh` exits with clear message before Gradle starts |
| Direct `./gradlew test` with services down | All scenarios FAIL with connection refused / timeout (Karate shows per-scenario cause) |
| Kafka not running | Feature 06 FAILS with `AssertionError: Expected N records...within Xms but got 0` |
| OpenObserve not running | Feature 10 FAILS with `AssertionError: No spans found...` |
| Feature 08 (`@ignore`) | Skipped unconditionally by Karate tag filter |
