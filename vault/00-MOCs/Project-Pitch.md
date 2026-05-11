---
title: Project Pitch — Real-Time Risk Lab
tags: [moc, project-pitch]
created: 2026-05-07
---

# Project Pitch — Real-Time Risk Lab

Un sistema de decisión de fraude en tiempo real explorado a través de tres arquitecturas Java, diseñado para validar trade-offs entre latencia in-process, aislamiento distribuido y simplicidad operacional.

## Espacio del problema

- 150 TPS de throughput sostenido
- Budget de latencia p99 < 300ms end-to-end
- Mezcla híbrida sync (respuesta de decisión) + async (auditoría, training ML, consumers downstream)
- Path de migración Lambda→EKS con deployments zero-downtime
- Requerimiento de explainability: las decisiones deben poder reconstruirse 6 meses después

## Arquitecturas exploradas

1. **Bare-javac** (`poc/no-vertx-clean-engine/`) — Clean Architecture sin frameworks. Dominio, ports y use cases implementados con `javac` puro. Sin Spring, sin Vert.x. Valida que la arquitectura hexagonal es una disciplina de código, no un feature de framework.
2. **Single-JVM monolith** (`poc/vertx-monolith-inprocess/`) — Vert.x con infraestructura completa: Postgres, Valkey, Redpanda, Floci (AWS S3/SQS/Secrets en un solo emulador). Baseline de latencia realista para producción.
3. **Layer-as-pod distribuido** (`poc/vertx-layer-as-pod-eventbus/`) — 4 JVMs con event bus de Hazelcast. Controller, usecase, repository y consumer como procesos independientes. Valida los límites de aislamiento bajo presión de deployment.

## Qué valida esto

- Trade-offs de performance medidos empíricamente (p50=125ns, p99=459ns in-process; benchmark de virtual threads en 1528 req/s)
- Patrones de aislamiento de fallas: reducción del blast radius por separación física de capas
- Reglas de negocio configuration-driven: motor de reglas + simulación de backoffice
- ATDD como quality gate: Karate (distribuido) + Cucumber-JVM (bare-javac), 10+ escenarios Gherkin verificados
- Diseño de SDK multi-lenguaje: clientes Java, TypeScript y Go compartiendo contratos de eventos

## Stack demostrado

- Java 21 (runtime 25), Gradle Kotlin DSL multi-módulo + version catalog + convention plugins
- Vert.x 5, cluster TCP de Hazelcast
- OpenTelemetry agent + spans/metrics custom + OpenObserve
- Floci (S3 + SQS + SNS + Secrets + KMS + STS + IAM en un solo endpoint, MIT, ADR-0042) — sin LocalStack (sunset en marzo 2026)
- k3d + ArgoCD + Argo Rollouts + AnalysisTemplates de Prometheus
- Go (smoke runner TUI con Bubble Tea)
- ArchUnit, JMH, Testcontainers, Karate, Cucumber-JVM

## Backlinks

[[Risk-Platform-Overview]] · [[Architecture-Question-Bank]] · [[Architectural-Anchors]] · [[Design-Anti-Patterns]] · [[Discovery-Questions]]
