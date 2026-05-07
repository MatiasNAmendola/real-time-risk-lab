---
title: Architecture MOC
tags: [moc, architecture]
created: 2026-05-07
updated: 2026-05-07
---

# Architecture MOC

## Patrones estructurales

- [[Clean-Architecture]] — anillos concéntricos, regla de dependencias hacia adentro
- [[Hexagonal-Architecture]] — variante ports & adapters
- [[Layer-as-Pod]] — nuestro patrón de PoC: cada capa arquitectural = su propio pod k8s

## Patrones de resiliencia

- [[Circuit-Breaker]] — fallar rápido, proteger el downstream
- [[Bulkhead]] — aislar dominios de falla
- [[Idempotency]] — semántica exactly-once

## Patrones asincrónicos

- [[Outbox-Pattern]] — outbox transaccional para publicación confiable de eventos
- [[Event-Versioning]] — evolución de schemas sin romper consumers
- [[DLQ]] — dead-letter queues, manejo de poison pill

## Decisiones — Plataforma Java

- [[0001-java-25-lts]] — Java 25 LTS como baseline
- [[0037-virtual-threads-http-server]] — virtual threads para concurrencia HTTP
- [[0031-no-di-framework]] — wiring manual en el Composition Root

## Decisiones — Arquitectura y layout

- [[0002-enterprise-go-layout-in-java]] — layout enterprise de Go traducido a Java
- [[0012-two-parallel-pocs]] — dos PoCs con scopes distintos
- [[0013-layer-as-pod]] — separación física de capas en Vert.x
- [[0020-pkg-shared-modules]] — módulos Gradle compartidos pkg/*
- [[0035-java-go-polyglot]] — Java para apps, Go para tooling de CLI
- [[0034-doc-driven-vault-structure]] — capas de documentación

## Decisiones — Sistemas distribuidos

- [[0003-vertx-for-distributed-poc]] — Vert.x 5 + cluster Hazelcast
- [[0030-redpanda-vs-kafka]] — Redpanda como broker Kafka local

## Decisiones — Eventos y confiabilidad

- [[0008-outbox-pattern-explicit]] — outbox transaccional
- [[0014-idempotency-keys-client-supplied]] — idempotency keys provistas por el cliente
- [[0015-event-versioning-field]] — campo eventVersion para evolución de schema
- [[0016-circuit-breaker-custom]] — circuit breaker hecho a mano

## Decisiones — Build y tooling

- [[0017-bare-javac-didactic-poc]] — bare-javac sin build tool al inicio
- [[0018-maven-before-gradle]] — Maven para PoCs antes que Gradle
- [[0019-gradle-kotlin-dsl]] — Gradle 8 + Kotlin DSL para pkg/*
- [[0026-convention-plugins]] — convention plugins en build-logic/

## Decisiones — Testing

- [[0006-atdd-karate-cucumber]] — ATDD dual con Karate + Cucumber-JVM
- [[0021-testcontainers-integration]] — Testcontainers para integration tests
- [[0032-jacoco-tcp-attach]] — JaCoCo TCP server para cobertura ATDD cross-module
- [[0033-moto-inline-vs-localstack]] — Moto server para integration tests AWS
- [[0036-archunit-structural-verification]] — ArchUnit para enforcement de la regla de dependencias

## Decisiones — Infraestructura local

- [[0004-openobserve-otel]] — OpenObserve como backend OTEL unificado
- [[0005-aws-mocks-stack]] — mocks AWS curados (sin LocalStack)
- [[0007-k3d-orbstack-switch]] — k3d + autodetección de OrbStack
- [[0027-orbstack-k3d-autodetect]] — OrbStack k8s built-in vs k3d
- [[0028-minio-agpl-acceptable]] — análisis de licencia AGPL de MinIO
- [[0029-openbao-vs-vault]] — OpenBao en lugar de HashiCorp Vault

## Decisiones — Tooling de IA

- [[0009-bubbletea-tui-smoke]] — Go + Bubble Tea para el smoke runner TUI
- [[0010-ide-agnostic-primitives]] — primitivas de IA agnósticas de IDE en .ai/
- [[0011-engram-mcp-memory]] — Engram MCP para memoria persistente del agente
- [[0022-reporting-dual-layer]] — reporting dual (consola + archivo)
- [[0023-smoke-runner-asymmetric]] — targets asimétricos del smoke runner
- [[0024-ai-directory]] — racional del directorio .ai/
- [[0025-skill-router-hybrid-scoring]] — skill router con scoring stdlib

## Índice completo

Ver [[_index]] para la tabla completa de ADRs ordenada por número.

## Implementaciones de PoC

- [[java-risk-engine]] — Clean Architecture, bare-javac, virtual threads
- [[java-vertx-distributed]] — Layer-as-Pod con Vert.x 5 + Hazelcast
- [[k8s-local]] — infraestructura de canary + rollout

## Backlinks

[[Risk-Platform-Overview]] linkea acá como entry point de arquitectura.
