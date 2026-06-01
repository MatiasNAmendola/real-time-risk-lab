---
name: typescript-poc-test-battery
description: Workflow para agregar y validar batería de tests en PoCs NestJS/Hono con Bun
steps: [read-rules, write-atdd, implement-tests, register-runner, verify, document]
---

# Workflow: typescript-poc-test-battery

## Cuándo usar

Cuando se agrega o refactoriza una PoC TypeScript (`nestjs-distributed-transactions` o `hono-distributed-transactions`) y se necesita cobertura unit/integration/e2e/smoke con ATDD.

## Pasos

1. Leer:
   - `.ai/primitives/rules/testing-atdd.md`
   - `.ai/primitives/rules/typescript-service-poc.md`
   - `.ai/primitives/rules/clean-arch-boundaries.md`
2. Escribir o actualizar `tests/atdd/*.feature`.
3. Implementar tests:
   - `src/**/*.test.ts` para unit;
   - `tests/integration/**/*.test.ts` para adapters;
   - `tests/e2e/**/*.test.ts` para HTTP real;
   - `tests/smoke/*.ts` para demo mínima.
4. Exponer scripts en `package.json` y `scripts/*.sh`.
5. Si se integra con el runner global, editar `.ai/test-groups.yaml` y `docs/27-test-runner.md`.
6. Verificar:
   ```bash
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
7. Documentar comandos y alcance de la suite en el README de la PoC.
