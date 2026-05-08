---
trigger: always_on
description: Real-Time Risk Lab project context and non-negotiable rules
---

# Proyecto: Real-Time Risk Lab — Architecture Exploration

Exploración técnica de arquitectura de riesgo transaccional.
Sistema de fraude tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 21 LTS baseline operativo (Java 25 LTS objetivo documentado), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Reglas non-negotiable

1. Java 21 LTS baseline operativo; Java 25 LTS queda como objetivo documentado.
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
