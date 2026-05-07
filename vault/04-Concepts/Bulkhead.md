---
title: Bulkhead
tags: [concept, pattern/resilience, distributed-systems]
created: 2026-05-07
---

# Bulkhead

Aísla dominios de falla particionando recursos (thread pools, connection pools, memoria). Una falla en un bulkhead no puede agotar los recursos que otro necesita. El nombre viene de los compartimentos de los barcos.

## Cuándo usar

Servicios multi-tenant, o servicios con workloads heterogéneos (fast path / slow path). A 150 TPS, una llamada ML síncrona lenta no debería matar de hambre a los threads de health check.

## Cuándo NO usar

Servicios single-tenant, single-workload donde el particionamiento agrega complejidad sin beneficio.

## En este proyecto

Un patrón común en servicios Go enterprise es implementar un bulkhead custom (basado en semáforos) sin librería. El PoC Vert.x usa event loop pools separados por verticle como bulkhead natural. Ver [[java-vertx-distributed]].

## Principio de diseño

"Bulkheads y circuit breakers son complementarios. El breaker deja de reintentar contra una dependencia caída; el bulkhead garantiza que esa caída no se lleve puesto al barco entero."

## Backlinks

[[Circuit-Breaker]] · [[Latency-Budget]] · [[Enterprise-Go-Layout-Reference]]
