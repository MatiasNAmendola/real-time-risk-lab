---
title: Hono Distributed Transactions PoC
tags: [poc, typescript, hono, cqrs, event-sourcing, saga, tigerbeetle, eda]
created: 2026-06-01
updated: 2026-06-01
---

# Hono Distributed Transactions

PoC TypeScript para contrastar la misma demostración transaccional contra NestJS usando un framework HTTP minimalista.

## Qué demuestra

- Misma cuenta bancaria simple con CQRS + Event Sourcing.
- Mismo caso avanzado de Saga/TigerBeetle/EDA.
- Wiring manual en `src/cmd/container.ts`.
- Rutas Hono + Zod sólo en infraestructura; dominio/aplicación no importan framework.

## Diferencia principal con NestJS

Hono deja más explícito el wiring y reduce magia de framework. NestJS muestra DI/decorators y `@nestjs/cqrs`. La duplicación de dominio es intencional para comparar experiencia de implementación, no una recomendación productiva.

## Tests

```bash
./nx test --composite hono-distributed-transactions-suite
```

Incluye unit, integration, e2e, ATDD feature, smoke y k6.

## Related

- [[nestjs-distributed-transactions]]
- [[CQRS-Event-Sourcing]]
- [[Saga-Pattern]]
- [[TigerBeetle-Ledger]]
- [[ATDD]]
- [[Idempotency]]
