---
name: add-nestjs-transactional-service
intent: Extender una PoC NestJS transaccional respetando Clean Architecture
inputs: [feature_name, controller_route, use_case, ports]
preconditions:
  - poc/nestjs-distributed-transactions existe
  - .ai/primitives/rules/typescript-service-poc.md leída
postconditions:
  - controllers/DTOs/handlers quedan en internal/infrastructure
  - nuevos use cases dependen sólo de puertos propios
  - tests ATDD/unit/integration/e2e/smoke actualizados
related_rules: [typescript-service-poc, clean-arch-boundaries, testing-atdd]
---

# Skill: add-nestjs-transactional-service

## Pasos

1. Definir el comportamiento en `tests/atdd/*.feature` antes de implementar.
2. Crear o extender el use case en `src/internal/application/usecase/<aggregate>/`.
3. Si necesita salida, definir puerto en `src/internal/domain/repository` o `src/internal/domain/service`.
4. Implementar adapter NestJS en `src/internal/infrastructure`:
   - controller en `infrastructure/controller`;
   - DTO con `class-validator` en `infrastructure/controller/dto`;
   - handlers CQRS en `infrastructure/cqrs`;
   - drivers BullMQ/Valkey/TigerBeetle en carpetas de infraestructura.
5. Wirear providers sólo en `src/cmd/app.module.ts`.
6. Agregar/actualizar tests:
   - unit para dominio/aplicación;
   - integration para adapters in-memory/fallback;
   - e2e HTTP;
   - smoke de demo.
7. Ejecutar:
   ```bash
   cd poc/nestjs-distributed-transactions
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
