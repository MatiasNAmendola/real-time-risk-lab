---
trigger: always_on
description: Real-Time Risk Lab project context and non-negotiable rules
---

# Proyecto: Real-Time Risk Lab — Technical Exploration

Exploración técnica de Real-Time Risk Lab technical leadership discussion.
Sistema de fraude tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS executable baseline, Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Reglas non-negotiable

1. Java 21 LTS es el baseline ejecutable (`--release 21`). Java 25 LTS es objetivo documentado, no requisito actual.
2. Clean Architecture layout (enterprise Go pattern). Ver .ai/primitives/rules/architecture-clean.md
3. ATDD primero. Escribir .feature ANTES del codigo de produccion.
4. OTEL en todo request: trace + log + metric. correlationId en MDC y header.
5. domain/ no importa de application/ ni infrastructure/.

## Skills disponibles

Antes de implementar, revisar: .ai/primitives/skills/
Rules completas: .ai/primitives/rules/
Workflows: .ai/primitives/workflows/

## No tocar

poc/, tests/, cli/, docs/, vault/ — ownership del usuario.
