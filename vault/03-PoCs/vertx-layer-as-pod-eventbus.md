---
title: vertx-layer-as-pod-eventbus PoC
tags: [poc, java, vertx, distributed]
created: 2026-05-07
source: poc/vertx-layer-as-pod-eventbus/ (también poc/vertx-layer-as-pod-http/)
---

# vertx-layer-as-pod-eventbus

Plataforma de riesgo distribuida con [[Layer-as-Pod]] usando Vert.x 5 + Hazelcast. Cada capa arquitectural (controller / usecase / repository) corre como contenedor JVM físicamente separado conectado vía event bus clustered de Vert.x.

## Qué demuestra

- [[Layer-as-Pod]] — capas de arquitectura como unidades deployables independientes
- Los 5 patrones de comunicación: REST, SSE, WebSocket, Webhooks, Kafka (ver [[Communication-Patterns]])
- [[Correlation-ID-Propagation]] vía MDC + atributos de span OTel
- Contrato OpenAPI 3.1 + Swagger UI + AsyncAPI 3.0
- Auto-instrumentación con OTel Java agent 2.x + métricas custom de Micrometer
- [[Circuit-Breaker]] en el borde inter-pod del event bus

## Stack

| Componente | Versión |
|------------|---------|
| Java | 25 LTS |
| Vert.x | 5.x |
| Hazelcast | 5.x (cluster manager TCP) |
| Gradle | multi-módulo |
| OTel Java agent | 2.x |
| Kafka |  Tansu (local) |

## Módulos

```
poc/vertx-layer-as-pod-eventbus/
  shared/           DTOs, codecs de eventos, utils de correlation
  controller-app/   ingreso HTTP: REST + SSE + WS + dispatch de Webhook
  usecase-app/      verticle de lógica de negocio: evaluación de riesgo
  repository-app/   verticle de data access: persistencia + publish a Kafka
  atdd-tests/       tests de aceptación con Karate DSL
```

## Cómo correrlo

```bash
cd poc/vertx-layer-as-pod-eventbus
docker-compose up --build
# controller: localhost:8080
# swagger ui: localhost:8080/swagger-ui
# asyncapi: localhost:8080/asyncapi
```

## Redes Docker

- `ingress` — tráfico HTTP externo hacia controller-app
- `eventbus` — Hazelcast + event bus de Vert.x entre todas las apps
- `data` — repository-app a DB/Kafka
- `telemetry` — OTel collector hacia OpenObserve

## Conceptos aplicados

[[Layer-as-Pod]] · [[Circuit-Breaker]] · [[Correlation-ID-Propagation]] · [[Outbox-Pattern]] · [[Event-Versioning]] · [[Idempotency]] · [[Virtual-Threads-Loom]]

## Decisiones

[[0003-vertx-for-distributed-poc]] · [[0001-java-25-lts]] · [[0004-openobserve-otel]]

## Talking points de diseño

- "Cada capa es una unidad deployable. Podés escalar la capa de usecase de forma independiente de la de repository — lo cual importa cuando el cuello de botella se mueve."
- El aislamiento de red por Docker network modela la segmentación de namespaces del EKS productivo.
- El contrato AsyncAPI 3.0 significa que los consumers de Kafka tienen un spec machine-readable.
