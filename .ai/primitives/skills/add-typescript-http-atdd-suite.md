---
name: add-typescript-http-atdd-suite
intent: Add ATDD, unit, integration, e2e, smoke, and k6 tests to a Bun TypeScript PoC
inputs: [poc_path, base_port, observable_behaviors]
preconditions:
  - poc_path contains package.json, bunfig.toml, src/main.ts, and src/internal/
  - .ai/primitives/rules/testing-atdd.md has been read
  - .ai/primitives/rules/typescript-service-poc.md has been read
postconditions:
  - tests/atdd/*.feature describes observable behavior
  - tests/e2e/*.test.ts runs HTTP scenarios against a real process
  - tests/integration/*.test.ts validates adapters without external network
  - tests/smoke/*.ts validates the minimal demo path
  - package.json exposes test:unit, test:integration, test:e2e, test:smoke, test:k6, and test:atdd
related_rules: [testing-atdd, typescript-service-poc, clean-arch-boundaries]
---

# Skill: add-typescript-http-atdd-suite

## Steps

1. **Write the `.feature` first** in `tests/atdd/<feature>.feature`.
   - Include one `@smoke` happy path.
   - Include at least one `@regression` unhappy path.
   - Describe HTTP-observable results, not internals.

2. **Create HTTP e2e tests** in `tests/e2e/<feature>.e2e.test.ts`.
   - Start `bun run src/main.ts` with a test `PORT`.
   - Wait for `/healthz` with retry/deadline, not fixed sleeps.
   - Send `X-Correlation-Id` and validate the response header.
   - Kill the process in `afterAll` or `finally`.

3. **Create integration tests** in `tests/integration/`.
   - Test in-memory/fallback adapters.
   - Do not require Docker for the default suite.
   - Tests with real Valkey/TigerBeetle must live in a separate script or explicitly skip when infra is missing.

4. **Create smoke tests** in `tests/smoke/http.smoke.ts`.
   - Accept `BASE_URL` when the service is already running.
   - If `BASE_URL` is missing, it may start a local process on `TEST_PORT`.
   - Validate only the minimal demo flow.

5. **Create k6 smoke/load tests** in `tests/k6/<feature>.js`.
   - Use `BASE_URL` to target an already-running service.
   - Define thresholds for `http_req_failed`, `http_req_duration`, and `checks`.
   - Keep it small by default (`2 VUs`, `10s`) for local demos.

6. **Update `package.json` scripts**:
   ```json
   {
     "test:unit": "bun test src",
     "test:integration": "bun test tests/integration",
     "test:e2e": "bun test tests/e2e",
     "test:smoke": "bun run tests/smoke/http.smoke.ts",
     "test:k6": "K6_REQUIRED=true ./scripts/k6-smoke.sh",
     "test:atdd": "cucumber-js \"tests/atdd/**/*.feature\" --require \"tests/atdd/steps/**/*.js\" --format progress"
   }
   ```

7. **Update `scripts/test.sh`** to run guardrails, build, unit, integration, e2e, and smoke.

8. **Register the group in `.ai/test-groups.yaml`** if the suite should appear in `./nx test --list`.
