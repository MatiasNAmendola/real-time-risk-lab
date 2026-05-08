---
title: Idempotency
tags: [concept, pattern/resilience, distributed-systems]
created: 2026-05-07
---

# Idempotency

Una operación es idempotente si aplicarla múltiples veces tiene el mismo efecto que aplicarla una sola vez. En sistemas distribuidos: el caller puede reintentar de forma segura sin crear side effects duplicados.

## Cuándo usar

Toda operación mutativa que cruza un límite de red: procesamiento de pagos, evaluación de riesgo, alta de órdenes. Esencial para sistemas con delivery at-least-once (SQS, Kafka sin exactly-once).

## Cuándo NO usar

Lecturas puras. Pipelines de agregación donde la deduplicación se maneja upstream.

## En este proyecto

`EvaluateRiskUseCase` chequea un `IdempotencyStore` in-memory key-eado por `transactionId`. Si la key existe, devuelve el resultado cacheado. Ver [[no-vertx-clean-engine]] y [[atdd-cucumber]] (feature 3: duplicate transaction).

## Principio de diseño

"La idempotency key es el contrato entre caller y servicio. Sin ella, los retries se vuelven bugs."

## Backlinks

[[Circuit-Breaker]] · [[Outbox-Pattern]] · [[no-vertx-clean-engine]] · [[atdd-karate]] · [[atdd-cucumber]]
