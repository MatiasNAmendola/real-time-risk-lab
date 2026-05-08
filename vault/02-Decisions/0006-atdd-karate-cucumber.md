---
adr: "0006"
title: Dual ATDD — Karate and Cucumber-JVM
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/atdd]
---

# ADR-0006: Dual ATDD Frameworks — Karate for Vert.x, Cucumber-JVM for bare-javac

## Estado

Aceptado el 2026-05-07.

## Contexto

ATDD coverage spans two Java PoCs with different integration surfaces. The Vert.x distributed PoC (`poc/vertx-layer-as-pod-eventbus/`) exposes HTTP, WebSocket, SSE, Webhook, and Kafka — a multi-protocol surface that requires step library support beyond pure HTTP assertions. The bare-javac PoC (`tests/risk-engine-atdd/`) exposes HTTP and event-driven behavior (outbox, idempotency) — a narrower surface where standard HTTP assertions and custom step definitions suffice.

For technical-leadership-level design conversations, demonstrating familiarity with only one ATDD framework is weaker than demonstrating range. Karate and Cucumber-JVM represent the two dominant Java ATDD frameworks with different trade-offs.

## Decisión

Use Karate DSL (`poc/vertx-layer-as-pod-eventbus/atdd-tests/`) for the Vert.x platform and Cucumber-JVM (`tests/risk-engine-atdd/`) for the bare-javac engine. Karate provides built-in step library for HTTP, WebSocket, SSE, and Kafka — no custom step definitions for protocol-level assertions. Cucumber-JVM requires custom step definitions but shows the BDD authoring pattern (defining steps for domain vocabulary).

## Alternativas consideradas

### Opción A: Karate for Vert.x + Cucumber-JVM for bare-javac (elegida)
- **Ventajas**: Each framework used where it fits best; Karate's built-in Kafka, WebSocket, and SSE step library eliminates custom step definition boilerplate for the multi-protocol Vert.x PoC; Cucumber-JVM's explicit step definitions demonstrate the domain vocabulary (Gherkin steps map to `Given the customer has N chargebacks in 90 days`); JaCoCo TCP server attach (ADR-0032) works in Karate via `JaCoCoAgent.dump()`; demonstrates familiarity with both frameworks to reviewers.
- **Desventajas**: Two test suites to maintain; Gherkin dialect differences (Karate allows JS expressions in steps; Cucumber-JVM requires pure Gherkin); developers must know both frameworks.
- **Por qué se eligió**: The multi-protocol Vert.x surface specifically benefits from Karate's built-in protocol support. Implementing WebSocket and SSE assertions in Cucumber-JVM would require significant custom step definition work that Karate provides for free.

### Opción B: Karate only for both PoCs
- **Ventajas**: Single framework; Karate's built-in HTTP client works for the bare-javac HTTP surface; lower maintenance; one test style to learn.
- **Desventajas**: Does not demonstrate Cucumber-JVM familiarity; Karate's step syntax is less readable to product managers than Cucumber-JVM's natural language steps; the bare-javac ATDD value is partly in showing domain vocabulary in Gherkin — Karate's JS-heavy syntax is less idiomatic for domain stakeholders.
- **Por qué no**: A technical leadership engineer should be conversant with Cucumber-JVM (the older, more widely known standard) and Karate (the newer, more capable framework). Showing only Karate omits Cucumber-JVM from the portfolio.

### Opción C: Cucumber-JVM only for both PoCs
- **Ventajas**: Maximum familiarity; Cucumber-JVM is more widely known in enterprise Java shops; explicit step definitions are readable by non-developers.
- **Desventajas**: Implementing Kafka, WebSocket, and SSE assertions in Cucumber-JVM step definitions requires writing a custom Kafka consumer, WebSocket client, and SSE reader — significant work that Karate provides built-in; JaCoCo integration is less mature in Cucumber-JVM's parallel execution mode.
- **Por qué no**: Custom protocol step definitions for WebSocket, SSE, and Kafka are non-trivial. Karate's built-in support is the right tool for the multi-protocol Vert.x surface.

### Opción D: JBehave
- **Ventajas**: Older BDD framework; used in some legacy enterprise Java shops.
- **Desventajas**: Significantly less active than Cucumber-JVM; thinner ecosystem; not used in the target stack based on investigation.
- **Por qué no**: JBehave's market share has declined sharply. Demonstrating JBehave knowledge signals familiarity with legacy tooling, not current practice.

## Consecuencias

### Positivo
- Karate ATDD suite covers all 9 communication channels (HTTP, WebSocket, SSE, Webhook, Kafka, OTEL) with minimal boilerplate.
- Cucumber-JVM demonstrates domain-vocabulary Gherkin that stakeholders can read and validate.
- JaCoCo TCP server attach on the Vert.x containers provides cross-module coverage data.

### Negativo
- Two test suites to maintain — test vocabulary may drift if domain terms change.
- Karate's JS step syntax is less natural for product managers than Cucumber-JVM's.

### Mitigaciones
- Shared Gherkin vocabulary documented in `poc/vertx-layer-as-pod-eventbus/atdd-tests/README.md`.
- Both suites test the same domain scenarios (APPROVE, REVIEW, DECLINE) — consistency is enforceable via review.

## Validación

- `cd poc/vertx-layer-as-pod-eventbus && ./gradlew -pl atdd-tests verify` passes all 10 Karate feature files.
- `cd tests/risk-engine-atdd && ./gradlew test jacocoTestReport` passes all Cucumber-JVM scenarios.
- Karate coverage report shows cross-module coverage from ATDD scenarios.

## Relacionado

- [[0032-jacoco-tcp-attach]]
- [[0021-testcontainers-integration]]
- Docs: doc 11 (`docs/11-atdd.md`)

## Referencias

- Karate DSL: https://github.com/karatelabs/karate
- Cucumber-JVM: https://github.com/cucumber/cucumber-jvm
- doc 11: `docs/11-atdd.md`
