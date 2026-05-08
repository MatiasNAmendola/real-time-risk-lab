---
title: Observability MOC
tags: [moc, observability, otel]
created: 2026-05-07
---

# Observability MOC

## Señales

- **Traces** — distributed tracing vía OTel Java agent 2.x; `correlationId` como atributo de span
- **Metrics** — métricas custom con Micrometer; scrape de Prometheus; AnalysisTemplate para gates de canary
- **Logs** — JSON estructurado; propagación de `correlationId` por MDC; envío a OpenObserve

## Conceptos

- [[SLI-SLO-Error-Budget]] — cómo definir y medir la salud del servicio
- [[Correlation-ID-Propagation]] — stitching de traces a través de hops sync + async
- [[Canary-Deployment]] — rollout progresivo gated por métricas Prometheus

## Stack

- OTel Java agent 2.x (auto-instrumentación)
- OpenObserve (backend, self-hosted en k3d)
- Prometheus + Grafana (kube-prom-stack)
- Métricas custom con Micrometer en Vert.x

Ver [[0004-openobserve-otel]] para el decision record.

## PoCs

- [[vertx-layer-as-pod-eventbus]] — traces OTEL, correlationId por MDC, métricas custom
- [[k8s-local]] — AnalysisTemplates de Prometheus para gates de canary

## Backlinks

[[Risk-Platform-Overview]] linkea acá como entry point de observabilidad.
