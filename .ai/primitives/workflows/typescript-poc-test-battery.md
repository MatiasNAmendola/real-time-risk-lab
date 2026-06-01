---
name: typescript-poc-test-battery
description: Workflow to add and validate a Bun-based test battery for NestJS/Hono PoCs
steps: [read-rules, write-atdd, implement-tests, register-runner, verify, document]
---

# Workflow: typescript-poc-test-battery

## When to use

Use when adding or refactoring a TypeScript PoC (`nestjs-distributed-transactions` or `hono-distributed-transactions`) and unit/integration/e2e/smoke coverage with ATDD is required.

## Steps

1. Read:
   - `.ai/primitives/rules/testing-atdd.md`
   - `.ai/primitives/rules/typescript-service-poc.md`
   - `.ai/primitives/rules/clean-arch-boundaries.md`
2. Write or update `tests/atdd/*.feature`.
3. Implement tests:
   - `src/**/*.test.ts` for unit coverage;
   - `tests/integration/**/*.test.ts` for adapters;
   - `tests/e2e/**/*.test.ts` for real HTTP;
   - `tests/smoke/*.ts` for the minimal demo.
4. Expose scripts in `package.json` and `scripts/*.sh`.
5. If integrating with the global runner, edit `.ai/test-groups.yaml` and `docs/27-test-runner.md`.
6. Verify:
   ```bash
   ./scripts/check-boundaries.sh
   bun run build
   bun run test:unit
   bun run test:integration
   bun run test:e2e
   bun run test:smoke
   ```
7. Document commands and suite scope in the PoC README.
