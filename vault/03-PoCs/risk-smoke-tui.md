---
title: risk-smoke-tui PoC
tags: [poc, go, tui, smoke-testing]
created: 2026-05-07
source: cli/risk-smoke/
---

# risk-smoke-tui

Smoke runner CLI en Go usando una TUI de Bubble Tea. Verifica los 9 canales de comunicación de la plataforma Vert.x distribuida en una sola invocación. Modo dual: TUI interactiva y `--headless` para CI.

## Qué demuestra

- Tooling CLI políglota junto a un codebase primario en Java
- Cobertura E2E de todos los [[Communication-Patterns]]: health, OpenAPI, AsyncAPI, REST, SSE, WS, Webhook, Kafka, trace OTEL
- Concurrencia de Go para checks async paralelos (SSE, WS, Kafka)

## Stack

| Componente | Versión |
|------------|---------|
| Go | 1.22+ |
| Bubble Tea | latest |
| Lip Gloss | latest |
| Bubbles | latest (spinner, list) |

## Cómo correrlo

```bash
cd cli/risk-smoke
go build -o risk-smoke .
cd cli/risk-smoke && go run . --target http://localhost:8080
cd cli/risk-smoke && go run . --target http://localhost:8080 --headless  # modo CI
```

## Checks (9 en total)

| # | Check | Canal |
|---|-------|-------|
| 1 | health | HTTP GET /health |
| 2 | openapi | HTTP GET /openapi.json |
| 3 | asyncapi | HTTP GET /asyncapi.json |
| 4 | rest | POST /api/v1/risk/evaluate |
| 5 | sse | GET /api/v1/risk/stream (primer evento) |
| 6 | ws | WS /api/v1/risk/ws (echo round-trip) |
| 7 | webhook | POST callback receiver |
| 8 | kafka | round-trip produce + consume |
| 9 | otel | propagación de trace (X-B3-TraceId) |

## Conceptos aplicados

[[Communication-Patterns]] · [[Correlation-ID-Propagation]]

## Decisiones

[[0009-bubbletea-tui-smoke]]

## Talking points de diseño

- El modo TUI convierte una demo en un dashboard en vivo — visible durante un screen share.
- El modo headless emite JSON estructurado para parseo en CI.
- El check 9 (trace OTEL) prueba que el correlationId viaja desde el ingreso HTTP, a través del event bus, hasta Kafka — observabilidad end-to-end.
