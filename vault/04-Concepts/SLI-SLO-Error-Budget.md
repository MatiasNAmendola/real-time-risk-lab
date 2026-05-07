---
title: SLI / SLO / Error Budget
tags: [concept, observability, slo, reliability]
created: 2026-05-07
---

# SLI / SLO / Error Budget

**SLI** (Service Level Indicator): la métrica que se mide (p. ej., fracción de requests que completan en <300ms).
**SLO** (Service Level Objective): el target (p. ej., 99.5% de requests bajo 300ms p99 en 30 días).
**Error Budget**: `1 - SLO` — el headroom de falla permitido. Cuando se agota el budget, se frena el trabajo de features y se enfoca en confiabilidad.

## Cuándo usar

Todo servicio con usuarios externos o dependencias downstream. El target de 150 TPS / 300ms p99 que conduce esta exploración es un SLO implícito.

## En este proyecto

Los AnalysisTemplates de Prometheus en [[k8s-local]] implementan promoción canary gated por SLI: `success_rate > 0.99` y `latency_p99 < 300ms`. Ese es el SLO aplicado al momento del deploy.

## Principio de diseño

"El error budget convierte a la confiabilidad en una métrica de producto de primer nivel. Cuando el budget está lleno, podés shippear rápido. Cuando está vacío, la confiabilidad es la feature."

## Backlinks

[[Observability]] · [[Canary-Deployment]] · [[Latency-Budget]] · [[k8s-local]]
