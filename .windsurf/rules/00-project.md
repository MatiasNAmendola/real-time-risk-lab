---
trigger: always_on
description: NaranjaX practica-entrevista project context and non-negotiable rules
---

# Proyecto: NaranjaX Transactional Risk — Interview Prep

Preparacion para entrevista tecnica de Naranja X (Staff/Architect, Transactional Risk).
Sistema de fraude tiempo real: 150 TPS, p99 < 300ms.
Stack: Java 25 LTS, Vert.x 5.0.12, Maven, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Reglas non-negotiable

1. Java 25 LTS canonico. NO downgrade a 21. NO upgrade a 26.
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
