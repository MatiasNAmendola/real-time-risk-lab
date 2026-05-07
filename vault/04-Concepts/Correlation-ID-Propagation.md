---
title: Correlation ID Propagation
tags: [concept, observability, distributed-tracing]
created: 2026-05-07
---

# Correlation ID Propagation

Un `correlationId` (o `traceId`) inyectado en el entry point y propagado a través de todos los hops: HTTP headers → MDC → headers de mensajes del event bus → headers de records Kafka → campos de log → atributos de span OTel. Permite reconstrucción end-to-end del trace cruzando bordes sincrónicos y asíncronos.

## Cuándo usar

Todo sistema distribuido. Sin él, debuggear una falla cross-service es arqueología.

## Cuándo NO usar

Monolitos single-process donde el call stack ya es el trace.

## En este proyecto

En [[java-vertx-distributed]]: el HTTP controller inyecta `X-Correlation-Id` en MDC y en el span OTel. Los mensajes del event bus lo llevan como header. Los records Kafka lo llevan como record header. Todos los logs lo incluyen vía MDC. El check 9 de [[risk-smoke-tui]] verifica propagación end-to-end.

## Principio de diseño

"El correlation ID es el hilo que cose un request a través de 4 hops y 3 protocolos. Sin él, tenés líneas de log, no una historia."

## Backlinks

[[Observability]] · [[SLI-SLO-Error-Budget]] · [[java-vertx-distributed]] · [[risk-smoke-tui]] · [[0004-openobserve-otel]]
