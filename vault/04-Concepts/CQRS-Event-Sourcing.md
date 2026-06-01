---
title: CQRS + Event Sourcing
tags: [concept, cqrs, event-sourcing, projections]
created: 2026-06-01
updated: 2026-06-01
---

# CQRS + Event Sourcing

CQRS separa comandos de escritura y queries de lectura. Event Sourcing guarda hechos de negocio append-only y reconstruye estado aplicando eventos.

## En este repo

El ejemplo canónico es la cuenta bancaria simple de las PoCs TypeScript:

- `OpenAccountCommand` → `AccountOpened`
- `DepositMoneyCommand` → `MoneyDeposited`
- `GetAccountBalanceQuery`
- Event store in-memory
- Balance projection
- Rehidratación y snapshot conceptual

La guía larga está en `docs/41-cqrs-event-sourcing-transacciones.md`.

## Límite importante

TigerBeetle no se usa para explicar Event Sourcing simple. TigerBeetle encaja mejor como ledger financiero para movimientos contables consistentes.

## Related

- [[nestjs-distributed-transactions]]
- [[hono-distributed-transactions]]
- [[TigerBeetle-Ledger]]
- [[Event-Versioning]]
