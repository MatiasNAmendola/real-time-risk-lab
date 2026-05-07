---
title: Hexagonal Architecture
tags: [concept, pattern/structural, architecture]
created: 2026-05-07
source: docs/04-clean-architecture-java.md
---

# Hexagonal Architecture

Patrón Ports & Adapters. El core de la aplicación define ports (interfaces); los adapters los implementan para tecnologías específicas (HTTP, SQS, JDBC). Múltiples adapters pueden enchufarse al mismo port.

## Cuándo usar

Cuando necesitás intercambiar adapters de forma independiente — p. ej., reemplazar SQS por Kafka sin tocar el dominio. También útil para testing: cambiar el adapter real de DB por uno in-memory.

## Cuándo NO usar

Apps two-tier simples donde el costo de abstracción no se justifica.

## En este proyecto

`EvaluateRiskUseCase` es un port. `HttpRiskController` y `SqsEventConsumer` son primary adapters. `InMemoryTransactionRepository` es secondary adapter (test). Ver [[java-risk-engine]].

## Principio de diseño

"El port no sabe si el mensaje vino de HTTP o de SQS. Eso es por diseño — el use case es agnóstico al canal."

## Backlinks

[[Clean-Architecture]] · [[Architecture]] · [[java-risk-engine]]
