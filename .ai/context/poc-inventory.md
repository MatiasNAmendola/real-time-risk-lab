# PoC inventory — context pointer

> La tabla canónica de PoCs vive en `AGENTS.md §3`. Este archivo es sólo un pointer.

Ver también:
- `docs/03-poc-roadmap.md` — narrativa de roadmap
- `vault/03-PoCs/Poc-Parity-Matrix.md` — matriz de paridad funcional + performance
- `vault/03-PoCs/` — una card por PoC con detalles
- `vault/00-MOCs/Risk-Platform-Overview.md` — MOC raíz

## PoC adicional — NestJS Distributed Transactions

- **Path**: `poc/nestjs-distributed-transactions`
- **Estado**: implementado como PoC complementaria
- **Demuestra**: cuenta bancaria simple con CQRS/Event Sourcing; demo avanzada de sagas, compensación/rollback, boundary TigerBeetle y EDA BullMQ/Valkey.
- **Run**: `cd poc/nestjs-distributed-transactions && ./scripts/test.sh`

## PoC adicional — Hono Distributed Transactions

- **Path**: `poc/hono-distributed-transactions`
- **Estado**: implementado como contraste de la PoC NestJS
- **Demuestra**: mismo ejemplo simple de CQRS/Event Sourcing con Hono + Zod + wiring manual; demo avanzada Saga/TigerBeetle/EDA separada.
- **Run**: `cd poc/hono-distributed-transactions && ./scripts/test.sh`

