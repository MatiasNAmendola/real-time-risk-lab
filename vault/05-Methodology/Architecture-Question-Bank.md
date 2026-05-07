---
title: Architecture Question Bank — 25 preguntas en 7 bloques
tags: [methodology, architecture, question-bank]
created: 2026-05-07
source: docs/09-architecture-question-bank.md
---

# Architecture Question Bank

25 preguntas en 7 bloques, desde `docs/09-architecture-question-bank.md`. Preparar un análisis de 2-3 oraciones para cada una. Ver [[Architectural-Anchors]] para los principios clave a embebir.

## Bloque 1 — Diseño de arquitectura

1. ¿Cómo diseñarías un sistema de evaluación de riesgo para 150 TPS con p99 < 300ms?
2. ¿Cuándo elegirías un path síncrono vs asíncrono para una decisión de riesgo?
3. ¿Cómo enforce-ás la regla de dependencias de [[Clean-Architecture]] en un equipo nuevo en eso?
4. ¿Cuál es la diferencia entre [[Hexagonal-Architecture]] y [[Clean-Architecture]] en la práctica?

## Bloque 2 — Java y JVM

5. ¿Cómo cambian los [[Virtual-Threads-Loom]] tu modelo de threading vs thread pools tradicionales?
6. ¿Cuándo usarías Vert.x reactivo por sobre virtual threads?
7. ¿Qué GC elegirías para risk scoring de baja latencia y por qué?
8. ¿Cómo profile-ás un servicio Java para encontrar al driver de la tail latency p99?

## Bloque 3 — AWS y EKS

9. Walk-through de una estrategia de migración Lambda→EKS para un servicio high-TPS.
10. ¿Cómo funciona [[IRSA]] y por qué es mejor que los instance profiles de EC2?
11. ¿Cuál es tu enfoque para gestión de secrets en EKS? (→ [[External-Secrets-Operator]])
12. ¿Cómo configurarías el HPA para el motor de riesgo? ¿Qué métrica usarías?

## Bloque 4 — Patrones de resiliencia

13. Explicá la máquina de estados de [[Circuit-Breaker]]. ¿Para qué sirve el estado Half-Open?
14. ¿Cómo protege un [[Bulkhead]] contra fallas en cascada?
15. ¿Qué es el [[Outbox-Pattern]] y cuándo es preferible a CDC?
16. ¿Cómo diseñás para [[Idempotency]] en una API de risk scoring?

## Bloque 5 — Kafka y eventos

17. ¿Cómo manejás los modos de compatibilidad de [[Schema-Registry]] (BACKWARD, FORWARD, FULL)?
18. ¿Cuál es tu estrategia de [[DLQ]] para un consumer Kafka que falla repetidamente con un mensaje?
19. ¿Cómo implementás [[Event-Versioning]] sin romper los consumers existentes?
20. ¿Cuál es la diferencia entre at-least-once y exactly-once en Kafka?

## Bloque 6 — Observabilidad

21. ¿Cómo propagás un [[Correlation-ID-Propagation|correlation ID]] desde HTTP a través de Kafka?
22. Walk-through definiendo un [[SLI-SLO-Error-Budget]] para este servicio.
23. ¿Cómo implementás un gate de [[Canary-Deployment]] usando Prometheus?

## Bloque 7 — Staff y Mentoring

24. ¿Cómo introducís [[ATDD]] a un equipo que solo hace unit tests?
25. Describí un momento en que cambiaste el enfoque de diseño de sistema de un equipo. ¿Qué resistencia enfrentaste?

## Backlinks

[[Risk-Platform-Overview]] · [[Architectural-Anchors]] · [[Design-Anti-Patterns]]
