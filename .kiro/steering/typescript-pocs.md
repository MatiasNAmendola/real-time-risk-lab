---
inclusion: fileMatch
filePatterns:
  - "poc/*-distributed-transactions/**/*.ts"
  - "poc/*-distributed-transactions/**/*.feature"
  - "poc/*-distributed-transactions/package.json"
---

# TypeScript PoCs

Aplicar `.ai/primitives/rules/typescript-service-poc.md`.

- Bun seguro con `ignoreScripts=true`.
- Clean Architecture: `src/cmd` + `src/internal/{domain,application,infrastructure}`.
- NestJS/Hono sólo en infrastructure/cmd.
- Tests mínimos por PoC: unit, integration, e2e, smoke y ATDD HTTP.
