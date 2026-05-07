---
title: Risk Platform Overview — Root MOC
tags: [moc, risk-platform]
created: 2026-05-07
---

# Risk Platform Overview — Root MOC

**Dominio**: Sistema de decisión de fraude en tiempo real
**Target SLA**: 150 TPS, p99 < 300ms
**Use case**: Detección de fraude grado producción explorada en tres arquitecturas Java

## Overview

Este vault mapea todo el material relacionado a la risk decision platform: decisiones de arquitectura, documentación de PoCs, metodología, notas de conceptos y build logs. El use case es detección de fraude en tiempo real con targets SLA productivos; la implementación es un artefacto de aprendizaje y exploración.

Ver [[2026-05-07-build-log]] para el log completo de la sesión.

## Arquitectura

Entry: [[Architecture]]

Decisiones clave:
- [[0001-java-25-lts]]
- [[0002-enterprise-go-layout-in-java]]
- [[0003-vertx-for-distributed-poc]]
- [[0007-k3d-orbstack-switch]]
- [[0008-outbox-pattern-explicit]]

Conceptos clave:
- [[Clean-Architecture]]
- [[Hexagonal-Architecture]]
- [[Layer-as-Pod]]
- [[Virtual-Threads-Loom]]
- [[Latency-Budget]]

## Estrategia de testing

Entry: [[Testing-Strategy]]

- [[ATDD]]
- [[TDD]]
- [[BDD]]
- [[0006-atdd-karate-cucumber]]

## Observabilidad

Entry: [[Observability]]

- [[SLI-SLO-Error-Budget]]
- [[Correlation-ID-Propagation]]
- [[0004-openobserve-otel]]

## Patrones de comunicación

Entry: [[Communication-Patterns]]

- [[Outbox-Pattern]]
- [[Event-Versioning]]
- [[DLQ]]
- [[Schema-Registry]]
- [[0010-ide-agnostic-primitives]]

## Tooling Stack

Entry: [[Tooling-Stack]]

- [[0005-aws-mocks-stack]]
- [[0009-bubbletea-tui-smoke]]
- [[IRSA]]
- [[External-Secrets-Operator]]

## PoCs

- [[java-risk-engine]] — Clean Architecture bare-javac
- [[java-vertx-distributed]] — Vert.x 4-módulos + cluster Hazelcast
- [[k8s-local]] — k3d + ArgoCD + canary rollouts
- [[risk-smoke-tui]] — smoke runner E2E con Go + Bubble Tea
- [[atdd-karate]] — ATDD con Karate para la plataforma Vert.x
- [[atdd-cucumber]] — ATDD con Cucumber-JVM para bare-javac

## Build Logs

- [[2026-05-07-build-log]] — día de implementación inicial cruzando las tres arquitecturas

## Metodología

- [[Architecture-Question-Bank]] — 25 preguntas de arquitectura en 7 bloques
- [[Architectural-Anchors]] — principios clave de diseño
- [[Design-Anti-Patterns]] — qué no hacer
- [[Discovery-Questions]] — preguntas de evaluación de plataforma
- [[Project-Pitch]] — descripción del proyecto y espacio del problema

## Docs adicionales

- [[30-consistency-audit]] — auditor de consistencia documental y cobertura de cross-references
- [[22-client-sdks]] — diseño de SDKs y estrategia de contratos multi-lenguaje
- [[25-barrier-shadow-circuit-modes]] — modos barrier, shadow y circuit para decisiones de riesgo
- [[27-test-runner]] — arquitectura del test runner y estrategia de ejecución
- [[28-cross-platform-support]] — decisiones de soporte cross-platform
- [[21-meta-coverage]] — auditoría meta-coverage y estrategia de cobertura
- [[23-go-version-policy]] — política de versión de Go y path de upgrade
- [[24-secrets-pii-protection]] — estrategia de protección de secrets y PII

## Próximos pasos

1. Repasar [[Architectural-Anchors]] — internalizar 3-5 principios
2. Releer bloque 1 (arquitectura) y bloque 4 (resiliencia) de [[Architecture-Question-Bank]]
3. Correr `poc/java-vertx-distributed` localmente, confirmar verde
4. Repasar [[Discovery-Questions]] — entender qué revelan las preguntas sobre el sistema
