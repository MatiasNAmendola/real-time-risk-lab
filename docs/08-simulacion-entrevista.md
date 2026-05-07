# 08 — Staff-Level Architecture Design Practice

Use this document to practice articulating design decisions at staff/architect level.

## Ronda 1 — System design

### Staff

Diseñá un sistema que evalúe fraude en tiempo real. Tenemos 150 TPS y queremos responder en 300ms.

### Tu respuesta esperada

- Confirmar p95/p99 vs promedio.
- Camino crítico vs async.
- Budget por dependencia.
- Timeouts/circuit breakers/fallbacks.
- Idempotencia.
- DecisionTrace.
- Outbox/eventos.

---

## Ronda 2 — Latencia

### Staff

El sistema está tardando 700ms p95. ¿Qué hacés?

### Tu respuesta esperada

- Trazas distribuidas.
- Breakdown por dependencia.
- DB/cache/ML/red/GC/locks/pools.
- No escalar antes de medir.
- Reducir hops/cachear/batch/optimizar queries.

---

## Ronda 3 — Lambda vs EKS

### Staff

¿Por qué te parece que deberíamos migrar de Lambda a EKS?

### Tu respuesta esperada

- No por moda.
- Cold starts/latencia variable/conexiones/tuning/observabilidad.
- EKS agrega complejidad.
- Runtime reemplazable.
- Pods calientes + HPA + probes + rollouts.

---

## Ronda 4 — Seguridad y pods

### Staff

¿Separarías controller, usecase y repository en pods distintos?

### Tu respuesta esperada

- Depende de trade-off latencia/complejidad.
- Sí si hay permisos distintos, escalado distinto o blast radius.
- Controller no toca DB.
- Usecase tiene permisos mínimos.
- Repository/outbox restringido.
- NetworkPolicy/IAM Role por ServiceAccount.

---

## Ronda 5 — Eventos

### Staff

¿Cómo diseñás eventos versionados y cómo evitás duplicados?

### Tu respuesta esperada

- Evento como API pública.
- eventId/eventVersion/correlationId/idempotencyKey.
- Backward-compatible.
- Consumers idempotentes.
- DLQ/retry.
- Outbox.

---

## Ronda 6 — ML

### Staff

El modelo ML a veces tarda 500ms. ¿Qué hacés?

### Tu respuesta esperada

- Timeout estricto.
- Circuit breaker.
- Fallback.
- Score cacheado/features precalculadas.
- Review si riesgo alto.
- Modelo no single point of failure.

---

## Ronda 7 — Auditoría

### Staff

Un cliente reclama una decisión de fraude de hace tres meses. ¿Qué necesitás tener?

### Tu respuesta esperada

- DecisionTrace persistida.
- Versiones de reglas/modelo/features.
- Score.
- Fallbacks.
- Correlation ID.
- Eventos emitidos.
- Logs/traces relacionados.

---

## Ronda 8 — Leadership

### Staff

¿Cómo harías para que el equipo mejore técnicamente?

### Tu respuesta esperada

- ADRs.
- Estándares de performance.
- Observabilidad como cultura.
- Contratos de eventos.
- Ownership.
- Mentoring por trade-offs, no recetas.
