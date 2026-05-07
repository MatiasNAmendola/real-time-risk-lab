# Project: Risk Decision Platform

Risk Decision Platform exploration: Transactional Risk (Staff/Architect scope).
Date: 2026-05-08

## PoCs
- poc/java-risk-engine: Clean Architecture bare-javac, no frameworks
- poc/java-vertx-distributed: 4 Maven modules as separate pods
- poc/vertx-risk-platform: Full Vert.x 5 with all comm patterns
- poc/k8s-local: k3d/OrbStack + ArgoCD + Argo Rollouts + kube-prom + AWS mocks

## Key decisions
- Java 25 LTS (ADR-001)
- Enterprise Go layout in Java (ADR-002)
- Vert.x 5 over Spring Boot (ADR-003)
- OpenObserve for OTEL (ADR-004)

Full decisions: .ai/context/decisions-log.md
