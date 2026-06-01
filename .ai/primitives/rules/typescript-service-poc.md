---
name: typescript-service-poc
applies_to: ["poc/*-distributed-transactions/src/**/*.ts", "poc/*-distributed-transactions/tests/**/*.ts", "poc/*-distributed-transactions/package.json"]
priority: high
---

# Rule: TypeScript service PoC

## Principle

TypeScript PoCs must demonstrate the pattern without breaking repository rules: safe Bun usage, Clean Architecture, and observable ATDD.

## Required layout

```text
src/
├── main.ts
├── cmd/                         # wiring/bootstrap
└── internal/
    └── transactional-risk/       # business capability first (Screaming Architecture)
        ├── domain/              # entities, value objects, ports
        ├── application/         # commands, pure DTOs, use cases
        └── infrastructure/      # HTTP, Nest/Hono, CQRS adapters, BullMQ, Valkey, TigerBeetle
```

## Required aliases

Avoid long cross-layer relative imports such as `../../domain`, `../../application`, or `../../infrastructure`.
Every TypeScript PoC must define aliases in `tsconfig.json`:

```json
"paths": {
  "@cmd/*": ["./src/cmd/*"],
  "@domain/*": ["./src/internal/transactional-risk/domain/*"],
  "@application/*": ["./src/internal/transactional-risk/application/*"],
  "@infrastructure/*": ["./src/internal/transactional-risk/infrastructure/*"],
  "@transactional-risk/*": ["./src/internal/transactional-risk/*"],
  "@internal/*": ["./src/internal/*"]
}
```

`bun run build` must rewrite aliases for `dist/` with `tsc-alias` or an equivalent tool.
Guardrails must treat `@infrastructure/*` as forbidden from `domain` and `application`.

## Boundaries

- `internal/transactional-risk/domain` must not import NestJS, Hono, BullMQ, ioredis, Valkey, TigerBeetle client, HTTP, or `internal/transactional-risk/infrastructure`.
- `internal/transactional-risk/application` must not import controllers, adapters, `@nestjs/*`, `hono`, `bullmq`, or `ioredis`.
- Frameworks and drivers live in `internal/transactional-risk/infrastructure`.
- `src/cmd` only composes dependencies.

## Required testing

Each TypeScript PoC must expose separate scripts:

```bash
bun run test:unit          # domain/application without infrastructure
bun run test:integration   # in-memory/fallback adapters without an HTTP server
bun run test:e2e           # real HTTP against a local process
bun run test:smoke         # minimal demo check
bun run test:k6            # HTTP smoke/load with k6 when applicable
bun run test:atdd          # executable .feature scenarios with Cucumber JS
```

## Safe Bun usage

- Use `bun install --frozen-lockfile`.
- Keep `bunfig.toml` with `[install] ignoreScripts = true`.
- Do not use npm, pnpm, or yarn for installs.

## Observability

Every public endpoint must propagate `X-Correlation-Id` and return it in the response when the framework allows it.

## k6

When the PoC exposes HTTP, add a lightweight k6 scenario for the main happy path.
k6 does not replace ATDD/e2e; it adds latency, percentile, and error-rate measurement under controlled concurrency.

- Suggested location: `tests/k6/<flow>.js`.
- Suggested script: `bun run test:k6`.
- In the global runner, declare `requires: [bun, k6]` so hosts without k6 report an explicit SKIP.
