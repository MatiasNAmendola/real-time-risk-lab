# GEMINI.md — Real-Time Risk Lab (Google Antigravity)

## Project

Technical exploration of real-time fraud detection architecture.
Target scenario: 150 sustained TPS, p99 < 300 ms.
Stack: Java 21 LTS executable baseline, Gradle Kotlin DSL, Vert.x 5, Postgres, Valkey, Tansu/Kafka, k3d/OrbStack.

Full context: `.ai/context/architecture.md`

## Non-negotiable rules

1. Java 21 LTS is the executable Gradle baseline (`--release 21`). Java 25 LTS is only a documented future target.
2. Clean Architecture: `domain/` must not import from `application/` or `infrastructure/`.
3. ATDD first: write the `.feature` before production code.
4. Every request must produce trace + structured log + metric through OpenTelemetry.
5. `correlationId` must be present in MDC and in the `X-Correlation-Id` response header.

## Skills and rules

Before implementing, check `.ai/primitives/skills/` and `.ai/primitives/rules/`.

## Editing scope

Edit `poc/`, `tests/`, `cli/`, `docs/`, and `vault/` only when the task explicitly requires it.
