---
title: Project Pitch — Risk Decision Platform
tags: [moc, project-pitch]
created: 2026-05-07
---

# Project Pitch — Risk Decision Platform

Un sistema de decisión de fraude en tiempo real explorado a través de tres arquitecturas Java, diseñado para validar trade-offs entre latencia in-process, aislamiento distribuido y simplicidad operacional.

## Espacio del problema

- 150 TPS de throughput sostenido
- Budget de latencia p99 < 300ms end-to-end
- Mezcla híbrida sync (respuesta de decisión) + async (auditoría, training ML, consumers downstream)
- Path de migración Lambda→EKS con deployments zero-downtime
- Requerimiento de explainability: las decisiones deben poder reconstruirse 6 meses después

## Arquitecturas exploradas

1. **Bare-javac** (`poc/java-risk-engine/`) — Clean Architecture sin frameworks. Dominio, ports y use cases implementados con `javac` puro. Sin Spring, sin Vert.x. Valida que la arquitectura hexagonal es una disciplina de código, no un feature de framework.
2. **Single-JVM monolith** (`poc/java-monolith/`) — Vert.x con infraestructura completa: Postgres, Valkey, Redpanda, MinIO, ElasticMQ. Baseline de latencia realista para producción.
3. **Layer-as-pod distribuido** (`poc/java-vertx-distributed/`) — 4 JVMs con event bus de Hazelcast. Controller, usecase, repository y consumer como procesos independientes. Valida los límites de aislamiento bajo presión de deployment.

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
- MinIO (S3), ElasticMQ (SQS), Moto (Secrets/SNS), OpenBao (Vault) — sin LocalStack
- k3d + ArgoCD + Argo Rollouts + AnalysisTemplates de Prometheus
- Go (smoke runner TUI con Bubble Tea)
- ArchUnit, JMH, Testcontainers, Karate, Cucumber-JVM

## Backlinks

[[Risk-Platform-Overview]] · [[Architecture-Question-Bank]] · [[Architectural-Anchors]] · [[Design-Anti-Patterns]] · [[Discovery-Questions]]
