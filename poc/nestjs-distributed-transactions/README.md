# PoC — NestJS Distributed Transactions

Exploración adicional del laboratorio para demostrar patrones transaccionales fuera del camino crítico del motor de riesgo Java/Vert.x.

Guía conceptual: [`../../docs/41-cqrs-event-sourcing-transacciones.md`](../../docs/41-cqrs-event-sourcing-transacciones.md).

## Dos niveles de demostración

### Nivel 1 — cuenta bancaria simple

Este es el recorrido principal y didáctico, cercano al ejemplo conceptual de CQRS + Event Sourcing:

- `OpenAccountCommand`
- `DepositMoneyCommand`
- `GetAccountBalanceQuery`
- eventos `AccountOpened` y `MoneyDeposited`
- `EventStore` in-memory append-only
- `BalanceProjection`
- rehidratación desde eventos
- snapshot como concepto (`BankAccount.snapshot()`)

Endpoints principales:

```http
POST /accounts/:id/open
POST /accounts/:id/deposit
GET  /accounts/:id
GET  /accounts/:id/events
```

TigerBeetle no participa en este nivel porque no aporta para explicar Event Sourcing base.

### Nivel 2 — demo avanzada separada

Bajo `/transactions/*` queda el caso extendido:

- **Patrón Saga / Saga orquestada** para una transacción distribuida: reserva de inventario, posteo contable, notificación downstream y compensating transactions si un paso falla.
- **Rollback/compensación** ante fallas simuladas después de inventario, después de ledger o en notificación.
- **TigerBeetle boundary** como puerto de ledger: útil para hablar de ledger real, transferencias idempotentes, cuentas débito/crédito e invariantes contables.
- **EDA** con BullMQ sobre Valkey, Webdis para inspección e idempotencia por `domainId` + checksum MD5 estable.
- **OTEL/correlationId**: todo request propaga `X-Correlation-Id`; OpenTelemetry exporta por OTLP HTTP si hay collector local.

## Layout

```text
src/
├── main.ts
├── cmd/                         # NestJS module/wiring
└── internal/
    ├── domain/{entity,event,repository,service,value-object}
    ├── application/{command,dto,event,mapper,usecase/transaction}
    └── infrastructure/{controller,eda,observability,repository,tigerbeetle}
```

El dominio define entidades, eventos, puertos y contratos. La infraestructura contiene controllers NestJS, repositorios in-memory/Valkey, BullMQ y el adapter TigerBeetle.


## Boundaries Clean Architecture

La PoC queda separada así:

- `domain/`: entidades, eventos de dominio, value objects y puertos. No importa NestJS, BullMQ, Valkey ni HTTP.
- `application/`: comandos, inputs puros, eventos de aplicación, mappers y use cases. No tiene decorators de NestJS ni `class-validator`.
- `infrastructure/`: controllers, DTOs HTTP con validación, handlers CQRS de Nest, BullMQ, Valkey, repositorios y adapter TigerBeetle.
- `src/cmd/app.module.ts` / `src/main.ts`: wiring de NestJS siguiendo la estructura `apps/<app>/cmd + internal/` del proyecto de referencia.

Guardrail local:

```bash
./scripts/check-boundaries.sh
```

Este script falla si `src/internal/domain` o `src/internal/application` importan NestJS, DTO decorators, BullMQ, Valkey/ioredis, HTTP, ORM/query libs o adapters de infraestructura.

## Requisitos

- Bun >= 1.3 con `[install] ignoreScripts = true`.
- Node compatible con NestJS 11 para `start:prod` si no se usa Bun.
- Valkey para EDA/idempotencia.
- TigerBeetle opcional para probar el boundary real.

## Correr

```bash
cd poc/nestjs-distributed-transactions
bun install --frozen-lockfile
bun run build
bun run start
```

Swagger queda en `http://localhost:3001/docs`.



## Demo simple: CQRS + Event Sourcing

```bash
curl -s -X POST http://localhost:3001/accounts/account-1/open \
  -H 'X-Correlation-Id: demo-open' | jq

curl -s -X POST http://localhost:3001/accounts/account-1/deposit \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-deposit' \
  -d '{"amount":100.50,"currency":"ARS"}' | jq

curl -s http://localhost:3001/accounts/account-1 | jq
curl -s http://localhost:3001/accounts/account-1/events | jq
```

La respuesta de `GET /accounts/:id` muestra el balance rehidratado desde el event stream y el balance de la proyección para contrastar Event Sourcing + read model.

## Patrón Saga aplicado

La PoC implementa una **Saga orquestada**. El use case coordina una secuencia de transacciones locales:

```text
reservar inventario
→ postear ledger
→ notificar downstream
→ si falla: compensar pasos previos ya confirmados
```

No hay rollback ACID global: cada paso confirma su propia operación local y la vuelta atrás se modela con compensaciones explícitas. Por eso la Saga guarda estado por step (`DONE`, `FAILED`, `SKIPPED`, `COMPENSATED`) y propaga `correlationId`.

## Demo saga exitosa

```bash
curl -s -X POST http://localhost:3001/transactions/sagas \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-saga-ok' \
  -d '{
    "transactionId":"tx-demo-ok",
    "debitAccountId":"payer-1",
    "creditAccountId":"merchant-1",
    "amount":120.50,
    "currency":"ARS",
    "scenario":"SUCCESS"
  }' | jq
```

Resultado esperado: `saga.status = COMPLETED` y los tres pasos en `DONE`.

## Demo rollback

```bash
curl -s -X POST http://localhost:3001/transactions/sagas \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-saga-rollback' \
  -d '{
    "transactionId":"tx-demo-rollback",
    "debitAccountId":"payer-1",
    "creditAccountId":"merchant-1",
    "amount":120.50,
    "currency":"ARS",
    "scenario":"FAIL_AFTER_LEDGER"
  }' | jq
```

Resultado esperado: `saga.status = COMPENSATED`, inventario compensado y una transferencia inversa en `compensationTransfer`.

## Demo avanzada: transferencia CQRS sobre proyecciones

```bash
curl -s -X POST http://localhost:3001/transactions/cqrs/accounts/payer-1/open -H 'X-Correlation-Id: demo-cqrs'
curl -s -X POST http://localhost:3001/transactions/cqrs/accounts/merchant-1/open -H 'X-Correlation-Id: demo-cqrs'
curl -s -X POST http://localhost:3001/transactions/cqrs/transfers \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-cqrs' \
  -d '{"transactionId":"tx-cqrs-1","debitAccountId":"payer-1","creditAccountId":"merchant-1","amount":50,"currency":"ARS"}'
curl -s http://localhost:3001/transactions/cqrs/accounts | jq
curl -s http://localhost:3001/transactions/events | jq
```

## Demo EDA con BullMQ + Valkey + Webdis

Levantar dependencias:

```bash
docker compose up -d redis webdis
```

Publicar mensaje de dominio:

```bash
curl -s -X POST http://localhost:3001/transactions/eda/messages \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-eda' \
  -d '{
    "domainId":"tx-eda-1",
    "domainType":"transaction",
    "eventType":"TransactionAccepted",
    "payload":{"amountCents":12050,"merchantId":"merchant-1"}
  }' | jq
```

Procesar manualmente el job usando el `jobId` devuelto:

```bash
curl -s -X POST http://localhost:3001/transactions/eda/jobs/<jobId>/process | jq
```

La idempotencia se calcula como `domainType:domainId:md5(payload-estable)`. Si llega el mismo mensaje, Valkey conserva el primer registro y el procesamiento responde `DUPLICATE_SAME_CHECKSUM`.

Inspección opcional vía Webdis:

```bash
curl -s http://localhost:7379/KEYS/eda:idempotency:* | jq
```

## TigerBeetle opcional

```bash
docker compose up -d tigerbeetle
TIGERBEETLE_ENABLED=true bun run start
```

El adapter mantiene la misma API del puerto `TigerBeetleLedger`; para review técnica se ve la frontera del ledger y el fallback evita bloquear la demo si el cluster local no está disponible.

## Verificación

```bash
./scripts/test.sh
```

## Next Steps

- Si se agregan nuevos módulos NestJS, mantener controllers/DTOs/handlers en `src/internal/infrastructure`.
- Si se agregan nuevos use cases, deben depender sólo de puertos propios del dominio/aplicación.
- El guardrail específico de esta PoC vive en `./scripts/check-boundaries.sh` y ya corre dentro de `./scripts/test.sh`.
- Si querés profundizar TigerBeetle real: reemplazar el fallback por llamadas completas a cuentas/transfers del cluster.
- Agregar ATDD HTTP estilo Karate/Cucumber para la nueva PoC si se quiere demo end-to-end externa.
- Agregar smoke check al CLI Go si esta PoC debe entrar en la demo principal.


## Batería de tests

Esta PoC sigue ATDD para comportamiento HTTP observable y separa la validación por costo:

```bash
bun run test:unit          # dominio/aplicación
bun run test:integration   # adapters in-memory/fallback
bun run test:e2e           # HTTP real contra proceso local
bun run test:smoke         # smoke mínimo para demo
bun run test:k6           # smoke/load HTTP con k6
bun run test:atdd          # ejecuta literalmente tests/atdd/*.feature con Cucumber JS
bun run test:atdd:e2e      # runner Bun equivalente del mismo contrato
./scripts/test.sh          # guardrails + build + batería principal
```

El contrato ATDD se ejecuta literalmente desde `tests/atdd/distributed-transactions.feature` con step definitions en `tests/atdd/steps/`. Además queda `tests/e2e/atdd-http.e2e.test.ts` como runner Bun equivalente para debugging rápido.


## k6 smoke/load HTTP

Además de ATDD/e2e funcional, la PoC incluye un escenario k6 liviano en `tests/k6/accounts-smoke.js`.

¿Por qué k6?

- ATDD valida comportamiento; k6 valida comportamiento bajo concurrencia HTTP mínima.
- Usa histogramas de latencia y percentiles (`p95`, `p99`) más confiables que loops caseros.
- Permite thresholds con exit code para CI.
- Es la misma herramienta ya adoptada por el repo para load testing HTTP.

Comando directo:

```bash
bun run test:k6
```

Si k6 no está instalado, la batería local `./scripts/test.sh` lo reporta como SKIP opcional mediante:

```bash
bun run test:k6:optional
```

Variables útiles:

```bash
K6_VUS=4 K6_DURATION=20s bun run test:k6
BASE_URL=http://localhost:3002 BASE_URL_ALREADY_RUNNING=1 bun run test:k6
```
