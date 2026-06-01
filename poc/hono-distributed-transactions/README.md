# PoC — Hono Distributed Transactions

PoC para contrastar Hono contra NestJS manteniendo dos niveles separados:

1. **Ejemplo simple principal**: cuenta bancaria con CQRS + Event Sourcing.
2. **Demo avanzada**: pago distribuido con Saga, compensaciones, TigerBeetle boundary y EDA BullMQ/Valkey.

El objetivo es que el primer recorrido sea didáctico y cercano al ejemplo conceptual: abrir cuenta, depositar dinero, consultar balance y auditar eventos.

## Nivel 1 — cuenta bancaria simple

Demuestra:

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

Ejemplo:

```bash
curl -s -X POST http://localhost:3002/accounts/account-1/open \
  -H 'X-Correlation-Id: demo-hono-open' | jq

curl -s -X POST http://localhost:3002/accounts/account-1/deposit \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-hono-deposit' \
  -d '{"amount":100.50,"currency":"ARS"}' | jq

curl -s http://localhost:3002/accounts/account-1 | jq
curl -s http://localhost:3002/accounts/account-1/events | jq
```

## Nivel 2 — demo avanzada separada

La demo avanzada queda bajo `/transactions/*` y muestra:

- Saga orquestada.
- Compensating transactions.
- Boundary `TigerBeetleLedger` con fallback in-memory.
- EDA con BullMQ sobre Valkey.
- Idempotencia por `domainId` + checksum MD5.

TigerBeetle no se usa para explicar Event Sourcing simple; queda reservado para el caso donde sí agrega valor: ledger financiero, transferencias idempotentes, cuentas débito/crédito e invariantes contables.

## Qué contrasta contra NestJS

| Tema | NestJS PoC | Hono PoC |
|---|---|---|
| HTTP | Controllers + decorators | Rutas explícitas Hono |
| Validation | `class-validator` en DTOs de infraestructura | `zod` en rutas de infraestructura |
| CQRS | `@nestjs/cqrs` | Command/query bus simple propio |
| Wiring | `src/cmd/app.module.ts` con DI container Nest | `src/cmd/container.ts` manual |
| Runtime mental model | Framework opinionated | Minimalista/funcional |
| Clean Architecture | `internal/domain` y `internal/application` sin framework | Igual, sin Hono/Zod/BullMQ en domain/application |


## Aliases TypeScript

Los imports entre capas usan aliases explícitos para evitar paths relativos largos y hacer visible la dirección de dependencia:

| Alias | Capa |
|---|---|
| `@domain/*` | entidades, eventos, value objects y puertos |
| `@application/*` | comandos, queries, DTOs internos, mappers y use cases |
| `@infrastructure/*` | controllers/routes, repositorios concretos, EDA, observabilidad y adapters |
| `@cmd/*` | composition root y wiring |

`tsconfig.json` define los aliases y `bun run build` ejecuta `tsc-alias` para reescribirlos en `dist/`, de modo que el build compilado también sea ejecutable. ESLint y los guardrails bloquean que `domain/` o `application/` importen `@infrastructure/*`.

## Layout

```text
src/
├── main.ts
├── cmd/
│   └── container.ts              # wiring manual estilo apps/<app>/cmd
└── internal/
    ├── domain/                   # entidades, eventos, puertos
    ├── application/              # comandos, queries, inputs, mappers, use cases
    └── infrastructure/           # Hono routes, CQRS simple, BullMQ, Valkey, repos, adapters
```

## Boundaries Clean Architecture

- `internal/domain`: entidades, eventos de dominio, value objects y puertos. No importa Hono, Zod, BullMQ, Valkey ni HTTP.
- `internal/application`: comandos, queries, inputs puros, eventos de aplicación, mappers y use cases. No tiene Hono, Zod ni adapters.
- `internal/infrastructure`: rutas Hono, schemas Zod, command/query bus simple, BullMQ, Valkey, repositorios y adapter TigerBeetle.
- `src/cmd/container.ts`: wiring manual.

Guardrail:

```bash
./scripts/check-boundaries.sh
```

## Correr

```bash
cd poc/hono-distributed-transactions
bun install --frozen-lockfile
bun run start
```

La app escucha en `http://localhost:3002`.

## Demo avanzada: Saga exitosa

```bash
curl -s -X POST http://localhost:3002/transactions/sagas \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-hono-saga-ok' \
  -d '{
    "transactionId":"tx-hono-ok",
    "debitAccountId":"payer-1",
    "creditAccountId":"merchant-1",
    "amount":120.50,
    "currency":"ARS",
    "scenario":"SUCCESS"
  }' | jq
```

## Demo avanzada: rollback Saga

```bash
curl -s -X POST http://localhost:3002/transactions/sagas \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-hono-rollback' \
  -d '{
    "transactionId":"tx-hono-rollback",
    "debitAccountId":"payer-1",
    "creditAccountId":"merchant-1",
    "amount":120.50,
    "currency":"ARS",
    "scenario":"FAIL_AFTER_LEDGER"
  }' | jq
```

## Demo avanzada: EDA con BullMQ + Valkey + Webdis

```bash
docker compose up -d redis webdis
VALKEY_PORT=6380 bun run start
```

```bash
curl -s -X POST http://localhost:3002/transactions/eda/messages \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-hono-eda' \
  -d '{
    "domainId":"tx-hono-eda-1",
    "domainType":"transaction",
    "eventType":"TransactionAccepted",
    "payload":{"amountCents":12050,"merchantId":"merchant-1"}
  }' | jq
```

Procesar manualmente el job usando el `jobId` devuelto:

```bash
curl -s -X POST http://localhost:3002/transactions/eda/jobs/<jobId>/process | jq
```

Inspección Webdis:

```bash
curl -s http://localhost:7380/KEYS/eda:idempotency:* | jq
```

## Verificación

```bash
./scripts/test.sh
bun run lint
```

## Batería de tests

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


## Apagado seguro

Para detener esta PoC sin depender de puertos hardcodeados:

```bash
# dry-run: muestra procesos y compose que se apagarían
bun run stop:dry-run

# ejecuta el apagado
bun run stop
```

El script identifica procesos locales por `cwd` dentro de la app y baja sólo el `docker-compose.yml` de esta PoC. Desde la raíz también se puede usar:

```bash
./nx stop typescript-transactional-pocs --yes
./nx stop all --yes
```
