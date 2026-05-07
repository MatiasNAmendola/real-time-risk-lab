---
title: Latency Budget
tags: [concept, performance, slo]
created: 2026-05-07
source: docs/05-budget-y-bottlenecks.md
---

# Latency Budget

Descomposición de la latencia total permitida (300ms p99) entre todos los hops: ingreso de red, auth, reglas de riesgo, modelo ML, escritura a DB, publicación de evento. Cada hop recibe una asignación; exceder cualquier asignación rompe el SLA.

## Cuándo usar

Siempre, cuando hay un SLA de latencia end-to-end. Fuerza una conversación honesta sobre dónde se va el tiempo.

## Cuándo NO usar

Jobs de background, procesamiento batch — el latency budget es para paths síncronos de cara al usuario.

## En este proyecto

Documentado en `docs/05-budget-y-bottlenecks.md`. Benchmark de [[java-risk-engine]]: p99=153ms deja ~147ms de headroom para red + DB. El estado open del [[Circuit-Breaker]] es fast-path: <1ms.

## Asignación de budget (ejemplo)

| Hop | Budget |
|-----|--------|
| Red + TLS | 10ms |
| Validación Auth/JWT | 5ms |
| Idempotency check | 2ms |
| Reglas de riesgo (CPU) | 15ms |
| Llamada al modelo ML | 50ms |
| Escritura a DB | 30ms |
| Outbox poll (async) | N/A |
| **Total** | **112ms** (headroom: 188ms) |

## Principio de diseño

"Presupuesto la latencia como si fuera dinero. Cada hop tiene asignación. Si el modelo ML se pasa de budget, o lo optimizamos o lo sacamos del hot path."

## Backlinks

[[Circuit-Breaker]] · [[Bulkhead]] · [[SLI-SLO-Error-Budget]] · [[java-risk-engine]]
