---
inclusion: always
---

# Product: Real-Time Risk Lab

Technical architecture exploration for Real-Time Risk Lab.
Real-time fraud detection: 150 TPS, p99 < 300ms.

## PoCs

- poc/no-vertx-clean-engine: Clean Architecture, no frameworks
- poc/vertx-layer-as-pod-eventbus: 4 Gradle modules, layer-as-pod
- poc/vertx-layer-as-pod-http: Full Vert.x 5 with all comm patterns
- poc/k8s-local: k3d/OrbStack + full k8s stack

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Editing project areas

You may edit `poc/`, `tests/`, `cli/`, `docs/` and `vault/` only when the task requires it. Before doing so, read the applicable rule/skill in `.ai/primitives/`.
