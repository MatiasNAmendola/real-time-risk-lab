---
title: OpenObserve Documentation
tags: [reference, observability, otel]
created: 2026-05-07
---

# OpenObserve Docs

Reference for OpenObserve used as OTEL backend in [[k8s-local]].

## Key URLs

- https://openobserve.ai/docs/ — official docs
- https://openobserve.ai/docs/ingestion/traces/otlp/ — OTLP ingestion
- https://openobserve.ai/docs/user-guide/queries/ — SQL-style query UI

## Key Configuration

OTLP endpoint: `http://openobserve:4317` (gRPC) or `http://openobserve:4318` (HTTP)
Default org: `default`
Default stream: `default`

In `k8s-local`, OpenObserve runs as a Deployment on the `telemetry` namespace. OTel Java agent configured with:
```
OTEL_EXPORTER_OTLP_ENDPOINT=http://openobserve:4317
OTEL_SERVICE_NAME=risk-engine-controller
```

## Why OpenObserve

OpenObserve was selected as a lightweight unified OTEL backend for local self-hosted use. See [[0004-openobserve-otel]] for the full alternatives analysis.

## Backlinks

[[k8s-local]] · [[Observability]] · [[0004-openobserve-otel]] · [[Correlation-ID-Propagation]]
