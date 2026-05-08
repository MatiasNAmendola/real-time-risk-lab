---
inclusion: always
---

# Product: Real-Time Risk Lab

Technical exploration for Real-Time Risk Lab real-time transactional risk patterns.
Real-time fraud detection: 150 TPS, p99 < 300ms.

## PoCs

- poc/no-vertx-clean-engine: Clean Architecture, no frameworks
- poc/vertx-layer-as-pod-eventbus: 4 Gradle modules, layer-as-pod
- poc/vertx-layer-as-pod-http: Full Vert.x 5 with all comm patterns
- poc/k8s-local: k3d/OrbStack + full k8s stack

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user ownership only.
