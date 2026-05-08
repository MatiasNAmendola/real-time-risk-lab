---
title: Circuit Breaker
tags: [concept, pattern/resilience, distributed-systems]
created: 2026-05-07
---

# Circuit Breaker

Máquina de estados con tres estados: Closed (normal), Open (fallando rápido), Half-Open (probando recuperación). Protege a los servicios downstream de cascadas de sobrecarga. Cuando la tasa de error supera el umbral, salta a Open y devuelve fallo rápido sin pegarle a la dependencia.

## Cuándo usar

Toda llamada síncrona a una dependencia externa (modelo ML, DB, servicio downstream) donde la latencia o falla puede hacer cascada. Esencial para el target de 300ms p99.

## Cuándo NO usar

Llamadas internas en proceso. Async fire-and-forget donde la falla es aceptable.

## En este proyecto

Implementación manual de 40 líneas en `infrastructure/resilience/CircuitBreaker.java`. Envuelve las llamadas al modelo ML y a servicios de riesgo downstream. [[ML-Online-Fallback]] se dispara cuando el circuito está Open. Ver [[no-vertx-clean-engine]] y feature 4 de [[atdd-karate]].

## Principio de diseño

"El circuit breaker es la diferencia entre un sistema degradado y un sistema muerto. Te compra tiempo para recuperarte sin cascada."

## Backlinks

[[Bulkhead]] · [[ML-Online-Fallback]] · [[Latency-Budget]] · [[no-vertx-clean-engine]] · [[vertx-layer-as-pod-eventbus]]
