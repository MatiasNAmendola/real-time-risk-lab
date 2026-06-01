---
trigger: always_on
description: Real-Time Risk Lab project context and non-negotiable rules
---

# Project: Real-Time Risk Lab — Architecture Exploration

Technical architecture exploration for transactional risk.
Real-time fraud system: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS executable baseline (Java 25 LTS documented target), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Non-negotiable rules

1. Java 21 LTS executable baseline; Java 25 LTS remains a documented target.
2. Clean Architecture layout (enterprise Go pattern). See .ai/primitives/rules/architecture-clean.md
3. ATDD first. Write the .feature file BEFORE production code.
4. OTEL on every request: trace + log + metric. correlationId in MDC and response header.
5. domain/ must not import from application/ or infrastructure/.

## Available skills

Before implementing, review: .ai/primitives/skills/
Full rules: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user-owned.
