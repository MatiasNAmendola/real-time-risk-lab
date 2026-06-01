---
name: add-hono-transactional-service
intent: Extender una PoC Hono transaccional respetando Clean Architecture y wiring explícito
inputs: [feature_name, route, use_case, ports]
preconditions:
  - poc/hono-distributed-transactions existe
  - .ai/primitives/rules/typescript-service-poc.md leída
postconditions:
  - rutas y schemas Zod quedan en internal/infrastructure/controller
  - wiring queda centralizado en src/cmd/container.ts
  - tests ATDD/unit/integration/e2e/smoke actualizados
related_rules: [typescript-service-poc, clean-arch-boundaries, testing-atdd]
---

# Skill: add-hono-transactional-service

## Pasos

1. Definir el comportamiento en `tests/atdd/*.feature` antes de implementar.
2. Crear o extender use case en `src/internal/application/usecase/<aggregate>/`.
3. Mantener validación HTTP con Zod en `src/internal/infrastructure/controller`.
4. Mantener el wiring manual en `src/cmd/container.ts`.
5. Si se agrega CQRS, usar un bus/handler explícito en `internal/infrastructure/cqrs`; no contaminar `application`.
6. Si se agrega EDA, BullMQ/Valkey quedan en `internal/infrastructure/eda` y `internal/infrastructure/repository`.
7. Ejecutar:
   ```bash
   cd poc/hono-distributed-transactions
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
