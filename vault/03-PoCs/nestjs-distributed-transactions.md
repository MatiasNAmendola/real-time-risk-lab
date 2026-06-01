---
title: NestJS Distributed Transactions PoC
tags: [poc, typescript, nestjs, cqrs, event-sourcing, saga, tigerbeetle, eda]
created: 2026-06-01
updated: 2026-06-01
---

# NestJS Distributed Transactions

PoC TypeScript para demostrar patrones transaccionales fuera del camino crítico del motor de riesgo Java/Vert.x.

## Qué demuestra

- Ejemplo simple principal: cuenta bancaria con CQRS + Event Sourcing.
- Demo avanzada: Saga orquestada, compensaciones, boundary TigerBeetle y EDA con BullMQ sobre Valkey.
- Clean Architecture con `src/cmd` + `src/internal/{domain,application,infrastructure}`.
- Contraste con Hono: framework opinionated, DI/decorators y `@nestjs/cqrs`.

## Recorrido simple

```http
POST /accounts/:id/open
POST /accounts/:id/deposit
GET  /accounts/:id
GET  /accounts/:id/events
```

Este recorrido explica [[CQRS-Event-Sourcing]] sin TigerBeetle para no mezclar ledger financiero con Event Sourcing de dominio.

## Recorrido avanzado

```http
POST /transactions/sagas
POST /transactions/eda/messages
POST /transactions/eda/jobs/:jobId/process
```

Muestra [[Saga-Pattern]], compensating transactions, idempotencia por `domainId` + checksum MD5 y [[TigerBeetle-Ledger]] como puerto de ledger.

## Tests

- Bun unit tests.
- Integration tests de adapters/fallbacks.
- E2E HTTP.
- ATDD `.feature` ejecutado con Cucumber JS.
- Smoke HTTP.
- k6 smoke/load HTTP.

Comando global:

```bash
./nx test --composite nestjs-distributed-transactions-suite
```

## Related

- [[hono-distributed-transactions]]
- [[CQRS-Event-Sourcing]]
- [[Saga-Pattern]]
- [[TigerBeetle-Ledger]]
- [[ATDD]]
- [[Idempotency]]
