---
title: TypeScript transactional PoCs para CQRS, Event Sourcing, Saga y ledger
status: accepted
date: 2026-06-01
tags: [adr, typescript, nestjs, hono, cqrs, event-sourcing, saga, ledger]
---

# ADR-0048: TypeScript transactional PoCs para CQRS, Event Sourcing, Saga y ledger

## Status

accepted

## Contexto

El laboratorio ya cubría la decisión de riesgo síncrona de baja latencia con Java/Vert.x, pero faltaba una demostración ejecutable de procesos transaccionales alrededor del pago: CQRS, Event Sourcing, Saga, compensaciones, ledger e idempotencia EDA.

## Decisión

Agregar dos PoCs TypeScript separadas:

- `poc/nestjs-distributed-transactions`: versión opinionated con NestJS, DI/decorators y `@nestjs/cqrs`.
- `poc/hono-distributed-transactions`: versión minimalista con Hono, Zod y wiring manual.

Ambas separan un ejemplo simple de cuenta bancaria para CQRS/Event Sourcing de una demo avanzada Saga/TigerBeetle/EDA bajo `/transactions/*`.

## Consecuencias positivas

- Permite contrastar framework opinionated vs framework minimalista sin cambiar el problema de negocio.
- Evita contaminar el path p99 < 300ms del motor de riesgo con workflows transaccionales más largos.
- Refuerza Clean Architecture en TypeScript con `src/cmd` + `src/internal/{domain,application,infrastructure}`.
- Integra ATDD `.feature`, smoke y k6 en el runner global.

## Consecuencias negativas / riesgos

- Duplica código entre NestJS y Hono para que el contraste sea autocontenido.
- Introduce stack TypeScript adicional, gobernado por Bun y guardrails de imports.
- TigerBeetle queda como boundary/fallback de demo; una integración productiva requiere cluster real, cuentas, transfers y reconciliación completa.

## Alternativas consideradas

- Extender las PoCs Vert.x: descartado para no mezclar decisión de riesgo síncrona con workflows financieros compensables.
- Sólo documentación: descartado porque el objetivo era demostrar ejecución, rollback y tests reales.
- Una sola PoC TypeScript: descartado porque no permitiría comparar NestJS vs Hono.
