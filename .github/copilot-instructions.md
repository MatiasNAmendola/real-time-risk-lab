# GitHub Copilot Instructions — Risk Decision Platform

## Project context

Risk Decision Platform — Three-Architecture Exploration.
Technical exploration of a real-time fraud detection use case: 150 TPS, p99 < 300ms latency.
Stack: Java 21 LTS executable baseline, Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md
Stack versions: .ai/context/stack.md

## Non-negotiable rules

1. Java 21 LTS is the executable baseline via Gradle toolchains (`--release 21`). Java 25 LTS is a documented future target only.
2. Clean Architecture layout: domain/{entity,repository,usecase,service,rule}, application/{usecase/<aggregate>,mapper,dto}, infrastructure/{controller,consumer,repository,resilience,time}, config/, cmd/.
3. domain/ must NOT import from application/ or infrastructure/ — ever.
4. ATDD first: write the .feature file before any production code.
5. Every request must produce trace + log + metric via OpenTelemetry. correlationId in MDC and response header.

## Available skills

Before implementing anything, check if there is a skill for it:
- .ai/primitives/skills/add-rest-endpoint.md
- .ai/primitives/skills/add-fraud-rule.md
- .ai/primitives/skills/add-kafka-publisher.md
- .ai/primitives/skills/add-otel-custom-span.md
- .ai/primitives/skills/add-resilience-pattern.md
... and 25+ more in .ai/primitives/skills/

## Available workflows

- .ai/primitives/workflows/new-feature-atdd.md
- .ai/primitives/workflows/deploy-to-k8s-local.md
- .ai/primitives/workflows/debug-trace-issue.md
... and more in .ai/primitives/workflows/

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user ownership only.
