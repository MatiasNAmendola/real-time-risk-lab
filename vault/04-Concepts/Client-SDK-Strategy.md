---
title: Client SDK Strategy — contratos compartidos, infraestructura encapsulada
tags: [concept, pattern/sdk, api, semver, contract-first, multi-language]
created: 2026-05-12
source_archive: docs/22-client-sdks.md (migrado 2026-05-12)
---

# Client SDK Strategy — contratos compartidos, infraestructura encapsulada

## Filosofía: encapsular la infraestructura, exponer el contrato

> "Si los equipos que nos consumen tienen que conocer Kafka, brokers, schemas, retry policies y ack semantics, fallamos. Los SDKs son la barrera entre el contrato (qué decidimos) y la infra (cómo se entrega). Cambia el broker, no cambian ellos."

Lo que el consumidor del SDK escribe:

```java
RiskClient client = RiskClient.builder()
    .endpoint(Environment.PROD)
    .apiKey(secrets.get("risk-engine-key"))
    .build();

RiskDecision decision = client.sync().evaluate(req);
client.events().consumeDecisions("my-consumer-group", this::handle);
client.webhooks().subscribe("https://my-service/callback", "DECLINE,REVIEW");
```

Lo que el consumidor **NO** sabe:
- URL del broker Kafka, nombre de topic, número de particiones.
- Si por debajo se está usando Tansu o Kafka managed.
- HTTP endpoint paths, headers de auth, retry policy, timeouts.
- Si el TLS es self-signed o ACM.

## Scope: los 7 canales de comunicación

```
RiskClient
├── .sync                              # REST síncrono
├── .stream                            # SSE — pull style server push
├── .channel                           # WebSocket bidirectional
├── .events                            # Kafka — encapsulado
├── .queue                             # SQS — encapsulado
├── .webhooks                          # Webhook
└── .admin                             # Backoffice (rules engine)
```

## SDKs en tres lenguajes

| SDK | Path en repo | Distribución | Stack interno |
|---|---|---|---|
| **risk-client-java** | `sdks/risk-client-java/` | JVM artifact registry | Java 21 LTS baseline + kafka-clients + AWS SDK v2 |
| **@riskplatform/risk-client** | `sdks/risk-client-typescript/` | npm | fetch nativo + EventSource + WebSocket + kafkajs + @aws-sdk/client-sqs |
| **risk-client-go** | `sdks/risk-client-go/` | Go module | net/http + coder/websocket + r3labs/sse + HTTP/gRPC event adapter + AWS SDK v2 Go |

## Contract-first development

La fuente de verdad NO es ningún SDK. Es:

- **OpenAPI 3.1** servido por Vertx en `/openapi.json`
- **AsyncAPI 3.0** servido en `/asyncapi.json` cubriendo Kafka topics + WebSocket channels + Webhook callbacks

Pipeline:

```
spec change → openapi.yaml + asyncapi.yaml updated in source
  → CI valida con openapi-validator + asyncapi-cli
  → CI corre codegen para los 3 SDKs (DTOs/types)
  → Hand-written wrappers en cada SDK quedan estables
  → CI bumpea version del SDK según diff (major/minor/patch)
  → CI publica al registry
```

## SemVer commitment

### Reglas de bumping

**MAJOR (X.0.0)** — breaking change. Requiere:
- Coexistencia de la versión previa por al menos 6 meses.
- Endpoint paralelo (`/v2/risk` mientras `/v1/risk` sigue activo).
- Migration playbook publicado.
- Header `Deprecation: true` + `Sunset: <date>` en responses de la versión vieja.
- Comunicación previa a todos los teams consumidores con 4 semanas de antelación mínima.

**MINOR (1.X.0)** — adición compatible (campo nuevo opcional, endpoint nuevo).

**PATCH (1.0.X)** — bugfix interno sin afectar API.

## Multi-language parity

| Operación | Java | TypeScript | Go |
|---|---|---|---|
| Sync evaluate | `client.sync().evaluate(req)` | `client.sync.evaluate(req)` | `client.Sync.Evaluate(ctx, req)` |
| SSE stream | `client.stream().decisions()` | `client.stream.decisions()` | `client.Stream.Decisions(ctx)` |
| Event consume | `client.events().consumeDecisions(group, handler)` | `client.events.consumeDecisions(group, handler)` | `client.Events.ConsumeDecisions(ctx, group, handler)` via HTTP/SSE adapter |

## Self-dogfooding

El **smoke runner Go** (`cli/risk-smoke/`) usa `sdks/risk-client-go`. Los smoke tests SON tests del SDK. Cualquier bug en el SDK se detecta antes de que afecte a un consumidor real.

## Test counts

| SDK | Unit tests | Integration tests | Framework |
|---|---|---|---|
| Java | 16 | 10 | JUnit 5 + Testcontainers |
| TypeScript | 14 | 9 | Jest + docker compose CLI |
| Go | 14 | 9 | testing + docker compose CLI + testify/require |
| Cross-SDK contract | — | 3 | JUnit 5 + Testcontainers + shell helpers |

## Anti-patterns

- **Cada equipo construye su HTTP client a mano**: cinco implementaciones de retry, cada una con bugs distintos.
- **Schema versioning a ojo**: cambiás el shape de un evento, descubrís en producción quién rompió.
- **Breaking change sin coexistencia**: deployás v2 al mismo tiempo que sacás v1, los consumers se enteran tarde.
- **SDK que expone la infra**: `client.kafkaProducer.send(topic, ...)` — el caller sabe que es Kafka.
- **SDK sin self-dogfooding**: el equipo del SDK no usa el SDK.

## Key Design Principles

> "El consumidor del SDK escribe `client.evaluate(req)` — no le importa si por debajo es REST, gRPC, RabbitMQ o un pichón mensajero. Eso es lo que diferencia una plataforma operable de un set de servicios que la gente sufre."

> "SemVer no es una decoración — es un contrato con los consumidores."

> "Encapsular la infraestructura es la única forma de cambiarla sin un evento de coordinación organizacional."

## Related

- [[Event-Versioning]] — versionado de contratos de eventos.
- [[Outbox-Pattern]] — garantía de publicación de eventos.
- [[Schema-Registry]] — validación de contratos en CI.
- [[Risk-Platform-Overview]]
