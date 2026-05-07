---
title: Outbox Pattern
tags: [concept, pattern/async, kafka, distributed-systems]
created: 2026-05-07
source: docs/06-eventos-versionados.md
---

# Outbox Pattern

Escribir el evento a una tabla `outbox` en la misma transacción de DB que el registro de negocio. Un proceso relay separado lee el outbox y publica al event broker. Garantiza delivery at-least-once sin transacciones distribuidas.

## Cuándo usar

Toda vez que escribís a una DB y tenés que publicar un evento — decisiones de riesgo, aprobaciones de pago, flags de fraude. El problema de dual-write (write DB + publish Kafka) es pérdida silenciosa de datos esperando ocurrir.

## Cuándo NO usar

Pipelines pure-append de alto throughput donde la tabla outbox se vuelve cuello de botella. Usar CDC (Debezium) en esa escala.

## En este proyecto

Agregado explícitamente a [[java-risk-engine]] como mejora arquitectural sobre el patrón direct-publish (ver [[0008-outbox-pattern-explicit]]). `OutboxRepository` es secondary port; `OutboxPoller` en infrastructure poll-ea y publica.

## Principio de diseño

"Sin el outbox tenés dos escrituras sin coordinador. Una de ellas va a fallar tarde o temprano. El outbox hace que la DB sea la única fuente de verdad."

## Backlinks

[[Architecture]] · [[Event-Versioning]] · [[DLQ]] · [[java-risk-engine]] · [[0008-outbox-pattern-explicit]]
