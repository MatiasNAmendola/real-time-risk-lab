# Agentes opencode — Real-Time Risk Lab

Ver el contexto principal: ../AGENTS.md

## Referencia rápida

Proyecto: Real-Time Risk Lab — Architecture Exploration
Stack: Java 21 LTS executable baseline (`--release 21`), Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack; Java 25 LTS is documented as a future target only
Primitivas: .ai/primitives/ (skills, rules, workflows, hooks)
Contexto: .ai/context/ (architecture, poc-inventory, decisions-log, stack)

## Antes de implementar cualquier cosa

1. Leer la rule aplicable: .ai/primitives/rules/<rule>.md
2. Buscar el skill: .ai/primitives/skills/<skill>.md
3. Seguir el workflow si es multi-step: .ai/primitives/workflows/<workflow>.md

## No tocar

poc/, tests/, cli/, docs/, vault/
