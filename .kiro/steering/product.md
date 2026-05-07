---
inclusion: always
---

# Product: NaranjaX practica-entrevista

Interview prep for NaranjaX Transactional Risk (Staff/Architect).
Real-time fraud detection: 150 TPS, p99 < 300ms.

## PoCs

- poc/java-risk-engine: Clean Architecture, no frameworks
- poc/java-vertx-distributed: 4 Maven modules, layer-as-pod
- poc/vertx-risk-platform: Full Vert.x 5 with all comm patterns
- poc/k8s-local: k3d/OrbStack + full k8s stack

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user ownership only.
