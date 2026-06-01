---
trigger: glob
glob: "poc/*-distributed-transactions/**/*.ts"
description: TypeScript NestJS/Hono PoCs with Bun, Clean Architecture and ATDD battery
---

# TypeScript PoC rules

Ver: .ai/primitives/rules/typescript-service-poc.md

- Usar Bun; no npm/pnpm/yarn para installs.
- Mantener `src/cmd` + `src/internal/{domain,application,infrastructure}`.
- `domain` y `application` no importan frameworks ni infrastructure.
- Todo comportamiento HTTP observable empieza por `tests/atdd/*.feature`.
- Mantener scripts: `test:unit`, `test:integration`, `test:e2e`, `test:smoke`, `test:atdd`.
