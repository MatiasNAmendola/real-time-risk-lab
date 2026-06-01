---
name: add-hono-transactional-service
intent: Extend a transactional Hono PoC while preserving Clean Architecture and explicit wiring
inputs: [feature_name, route, use_case, ports]
preconditions:
  - poc/hono-distributed-transactions exists
  - .ai/primitives/rules/typescript-service-poc.md has been read
postconditions:
  - routes and Zod schemas remain under internal/infrastructure/controller
  - wiring remains centralized in src/cmd/container.ts
  - ATDD, unit, integration, e2e, and smoke tests are updated
related_rules: [typescript-service-poc, clean-arch-boundaries, testing-atdd]
---

# Skill: add-hono-transactional-service

## Steps

1. Define behavior in `tests/atdd/*.feature` before implementation.
2. Create or extend the use case in `src/internal/application/usecase/<aggregate>/`.
3. Keep HTTP validation with Zod under `src/internal/infrastructure/controller`.
4. Keep manual wiring in `src/cmd/container.ts`.
5. If CQRS is added, use an explicit bus/handler under `internal/infrastructure/cqrs`; do not contaminate `application`.
6. If EDA is added, keep BullMQ/Valkey under `internal/infrastructure/eda` and `internal/infrastructure/repository`.
7. Run:
   ```bash
   cd poc/hono-distributed-transactions
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
