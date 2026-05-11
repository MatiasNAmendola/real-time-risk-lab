---
title: Communication Patterns MOC
tags: [moc, communication, api]
created: 2026-05-07
---

# Communication Patterns MOC

## Sincrónicos

- **REST** — JSON estándar sobre HTTP/1.1; spec OpenAPI 3.1 en Vert.x
- **SSE** (Server-Sent Events) — streaming unidireccional para updates de risk score
- **WebSocket** — bidireccional; canal de auditoría en tiempo real

## Asincrónicos

- **Webhook** — callbacks HTTP outbound sobre eventos de riesgo
- **Kafka** — backbone de eventos; Tansu (ADR-0043) como broker para dev local
- [[Outbox-Pattern]] — garantiza publish at-least-once desde la transacción de DB
- [[Event-Versioning]] — evolución de schema, contrato AsyncAPI 3.0

## Dead Letters

- [[DLQ]] — qué pasa cuando un consumer falla repetidamente

## Schemas

- [[Schema-Registry]] — compatible con Confluent; evita romper consumers

## Implementaciones

- [[vertx-layer-as-pod-eventbus]] — los 5 patrones (REST, SSE, WS, Webhook, Kafka) en una sola plataforma
- [[risk-smoke-tui]] — smoke test de 9 checks que cubre cada canal

## Backlinks

[[Risk-Platform-Overview]] linkea acá como entry point de comunicación.
