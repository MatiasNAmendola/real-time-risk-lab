---
name: communication-patterns
applies_to: ["**/infrastructure/controller/**/*.java", "**/infrastructure/consumer/**/*.java"]
priority: high
---

# Regla: communication-patterns

## Patrones soportados

| Patron | Protocolo | Caso de uso | Endpoint ejemplo |
|---|---|---|---|
| REST sync | HTTP/JSON | Evaluacion puntual, consultas | POST /risk |
| SSE streaming | HTTP/text-event-stream | Feed en tiempo real de decisiones | GET /risk/stream |
| WebSocket | WS | Evaluacion interactiva bidireccional | WS /ws/risk |
| Webhook | HTTP callback | Notificacion a sistemas externos | POST /webhooks/subscriptions |
| Kafka async | Redpanda topics | Integracion asincrona downstream | topic: risk-decisions |

## REST

- Content-Type: `application/json`.
- Versionado en path: `/api/v1/` (cuando hay multiple versiones).
- Errores: 4xx con body `{ "error": "...", "correlationId": "..." }`.
- OpenAPI spec SIEMPRE actualizada al agregar/modificar endpoints.

## SSE

- Content-Type: `text/event-stream`.
- Formato: `event: <type>\ndata: <json>\n\n`.
- Reconexion: incluir `id:` field para Last-Event-ID.
- AsyncAPI spec actualizada.

## WebSocket

- Upgrade explícito desde HTTP GET.
- Mensajes en JSON.
- Ping/pong para keepalive cada 30s.
- AsyncAPI spec actualizada.

## Webhook

- Firma HMAC-SHA256 en header `X-Webhook-Signature: sha256=<hex>`.
- Reintentos con backoff exponencial: 3 intentos.
- Payload incluye todos los campos de events-versioning.

## Kafka/Redpanda

- Topics: kebab-case. Ejemplo: `risk-decisions`, `fraud-alerts`.
- Particiones: 6 (default para demos locales).
- Schema: JSON con campos de events-versioning obligatorios.
- Consumer groups: `<service>-<purpose>`. Ejemplo: `notification-risk-decisions`.

## Restricciones

- No mezclar logica de negocio con codigo de transporte en el mismo archivo.
- No bloquear el Vert.x event loop en handlers (usar `.executeBlocking()` si es necesario).
- REST y eventos async pueden coexistir: REST para respuesta sincrona, evento para integración downstream.
