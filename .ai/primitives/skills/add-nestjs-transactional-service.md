---
name: add-nestjs-transactional-service
intent: Extend a transactional NestJS PoC while preserving Clean Architecture
inputs: [feature_name, controller_route, use_case, ports]
preconditions:
  - poc/nestjs-distributed-transactions exists
  - .ai/primitives/rules/typescript-service-poc.md has been read
postconditions:
  - controllers, DTOs, and handlers remain under internal/transactional-risk/infrastructure
  - new use cases depend only on their own ports
  - ATDD, unit, integration, e2e, and smoke tests are updated
related_rules: [typescript-service-poc, clean-arch-boundaries, testing-atdd]
---

# Skill: add-nestjs-transactional-service

## Steps

1. Define behavior in `tests/atdd/*.feature` before implementation.
2. Create or extend the use case in `src/internal/transactional-risk/application/usecase/<aggregate>/`.
3. If output is needed, define a port in `src/internal/transactional-risk/domain/repository` or `src/internal/transactional-risk/domain/service`.
4. Implement the NestJS adapter under `src/internal/transactional-risk/infrastructure`:
   - controller in `infrastructure/controller`;
   - DTO with `class-validator` in `infrastructure/controller/dto`;
   - CQRS handlers in `infrastructure/cqrs`;
   - BullMQ/Valkey/TigerBeetle drivers in infrastructure folders.
5. Wire providers only in `src/cmd/app.module.ts`.
6. Add or update tests:
   - unit tests for domain/application;
   - integration tests for in-memory/fallback adapters;
   - HTTP e2e tests;
   - demo smoke tests.
7. Run:
   ```bash
   cd poc/nestjs-distributed-transactions
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
