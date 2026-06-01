---
name: typescript-service-poc
applies_to: ["poc/*-distributed-transactions/src/**/*.ts", "poc/*-distributed-transactions/tests/**/*.ts", "poc/*-distributed-transactions/package.json"]
priority: high
---

# Regla: TypeScript service PoC

## Principio

Las PoCs TypeScript deben demostrar el patrón sin romper las reglas del repo: Bun seguro, Clean Architecture y ATDD observable.

## Layout obligatorio

```text
src/
├── main.ts
├── cmd/                         # wiring/bootstrap
└── internal/
    ├── domain/                  # entidades, value objects, puertos
    ├── application/             # comandos, DTOs puros, use cases
    └── infrastructure/          # HTTP, Nest/Hono, CQRS adapters, BullMQ, Valkey, TigerBeetle
```

## Boundaries

- `internal/domain` no importa NestJS, Hono, BullMQ, ioredis, Valkey, TigerBeetle client, HTTP ni `internal/infrastructure`.
- `internal/application` no importa controllers, adapters, `@nestjs/*`, `hono`, `bullmq` ni `ioredis`.
- Frameworks y drivers viven en `internal/infrastructure`.
- `src/cmd` sólo compone dependencias.

## Testing obligatorio

Cada PoC TypeScript debe exponer scripts separados:

```bash
bun run test:unit          # dominio/aplicación sin infra
bun run test:integration   # adapters in-memory/fallback sin servidor HTTP
bun run test:e2e           # HTTP real contra proceso local
bun run test:smoke         # chequeo mínimo para demo
bun run test:k6           # smoke/load HTTP con k6 si aplica
bun run test:atdd          # ejecuta .feature con Cucumber JS
```

## Bun seguro

- Usar `bun install --frozen-lockfile`.
- Mantener `bunfig.toml` con `[install] ignoreScripts = true`.
- No usar npm, pnpm ni yarn para installs.

## Observabilidad

Todo endpoint público debe propagar `X-Correlation-Id` y devolverlo en la respuesta cuando el framework lo permita.


## k6

Cuando la PoC expone HTTP, agregar un escenario k6 liviano para el happy path principal. k6 no reemplaza ATDD/e2e: agrega medición de latencia, percentiles y error rate bajo concurrencia controlada.

- Ubicación sugerida: `tests/k6/<flow>.js`.
- Script sugerido: `bun run test:k6`.
- En el runner global declarar `requires: [bun, k6]` para que hosts sin k6 hagan SKIP explícito.
