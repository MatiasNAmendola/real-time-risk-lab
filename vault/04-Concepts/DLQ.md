---
title: DLQ — Dead Letter Queue
tags: [concept, pattern/async, kafka, sqs]
created: 2026-05-07
---

# DLQ

Dead Letter Queue. Los mensajes que fallan al procesarse N veces se mueven a una DLQ en lugar de bloquear la cola principal. Permite inspección, replay o descarte sin frenar al consumer sano.

## Cuándo usar

Todo consumer at-least-once. SQS y Kafka soportan ambos semántica DLQ. Esencial para un pipeline de riesgo en tiempo real donde un evento malformado no debería bloquear todas las evaluaciones siguientes.

## Cuándo NO usar

Pipelines exactly-once con consumers idempotentes donde el retry es inocuo.

## En este proyecto

Referenciado en `docs/06-eventos-versionados.md`. DLQ de SQS cableada en [[0005-aws-mocks-stack]] (ElasticMQ soporta config de DLQ). La DLQ de Kafka es un topic separado por convención.

## Principio de diseño

"La DLQ es la válvula de seguridad. Convierte una falla bloqueante en una falla observable — podés alertar sobre la profundidad de la DLQ y replayar cuando estés listo."

## Backlinks

[[Outbox-Pattern]] · [[Event-Versioning]] · [[Schema-Registry]] · [[Communication-Patterns]]
