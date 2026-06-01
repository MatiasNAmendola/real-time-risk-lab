---
title: TigerBeetle Ledger Boundary
tags: [concept, ledger, tigerbeetle, distributed-transactions]
created: 2026-06-01
updated: 2026-06-01
---

# TigerBeetle Ledger Boundary

TigerBeetle encaja como ledger financiero: cuentas débito/crédito, transferencias idempotentes e invariantes contables de alta performance.

## En este repo

Las PoCs TypeScript lo aíslan detrás del puerto `TigerBeetleLedger`. El fallback in-memory mantiene la demo reproducible; el cluster real queda como siguiente profundización.

## Qué NO hace

- No orquesta el workflow de negocio.
- No reemplaza Event Sourcing de dominio.
- No define compensaciones de Saga.

## Qué sí aporta

- Ledger consistente.
- Transferencias idempotentes.
- Modelo contable explícito.
- Boundary claro entre dominio/eventos y movimientos financieros.

## Related

- [[Saga-Pattern]]
- [[CQRS-Event-Sourcing]]
- [[Idempotency]]
