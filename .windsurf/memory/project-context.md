# Project: Real-Time Risk Lab

Real-Time Risk Lab exploration: real-time transactional risk patterns.
Date: 2026-05-08

## PoCs
- poc/no-vertx-clean-engine: Clean Architecture bare-javac, no frameworks
- poc/vertx-layer-as-pod-eventbus: 4 Gradle modules as separate pods
- poc/vertx-layer-as-pod-http: Full Vert.x 5 with all comm patterns
- poc/k8s-local: k3d/OrbStack + ArgoCD + Argo Rollouts + kube-prom + AWS mocks

## Key decisions
- Java 21 LTS como baseline ejecutable; Java 25 LTS como objetivo documentado (ADR-001)
- Enterprise Go layout in Java (ADR-002)
- Vert.x 5 over Spring Boot (ADR-003)
- OpenObserve for OTEL (ADR-004)

Full decisions: .ai/context/decisions-log.md
