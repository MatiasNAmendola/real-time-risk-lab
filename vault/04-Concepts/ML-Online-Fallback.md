---
title: ML Online Fallback
tags: [concept, pattern/resilience, ml]
created: 2026-05-07
source: docs/08-ml-online.md
---

# ML Online Fallback

Estrategia para motores de riesgo que llaman a un modelo ML online de forma síncrona. Cuando el modelo no está disponible (circuit open, timeout, 5xx), se hace fallback a scoring determinístico basado en reglas. El fallback debe ser conservador (una tasa más alta de falsos positivos es aceptable; los falsos negativos no).

## Cuándo usar

Todo path de scoring de riesgo o fraude que use un modelo ML en tiempo real como scorer primario. La latencia o disponibilidad del modelo no puede estar en el critical path sin un fallback.

## Cuándo NO usar

Pipelines de scoring batch donde el modelo se pre-computa offline.

## En este proyecto

`RiskScoringService` en [[java-risk-engine]] llama al modelo ML vía port; [[Circuit-Breaker]] envuelve la llamada. En estado Open, `FallbackRuleEngine` aplica reglas de velocidad y monto de forma determinística. Ver feature 4 de [[atdd-cucumber]].

## Principio de diseño

"El modelo ML es una optimización, no una dependencia. El sistema debe tomar una decisión de riesgo con o sin él — el fallback no es un modo degradado, es el comportamiento diseñado."

## Backlinks

[[Circuit-Breaker]] · [[Latency-Budget]] · [[java-risk-engine]]
