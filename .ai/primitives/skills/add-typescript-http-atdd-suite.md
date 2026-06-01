---
name: add-typescript-http-atdd-suite
intent: Agregar batería ATDD/unit/integration/e2e/smoke a una PoC TypeScript con Bun
inputs: [poc_path, base_port, observable_behaviors]
preconditions:
  - poc_path contiene package.json, bunfig.toml, src/main.ts y src/internal/
  - .ai/primitives/rules/testing-atdd.md leída
  - .ai/primitives/rules/typescript-service-poc.md leída
postconditions:
  - tests/atdd/*.feature describe el comportamiento observable
  - tests/e2e/*.test.ts ejecuta los escenarios HTTP contra un proceso real
  - tests/integration/*.test.ts valida adapters sin red externa
  - tests/smoke/*.ts valida el path mínimo de demo
  - package.json expone test:unit, test:integration, test:e2e, test:smoke, test:k6 y test:atdd
related_rules: [testing-atdd, typescript-service-poc, clean-arch-boundaries]
---

# Skill: add-typescript-http-atdd-suite

## Pasos

1. **Escribir el `.feature` primero** en `tests/atdd/<feature>.feature`.
   - Incluir un happy path `@smoke`.
   - Incluir al menos un unhappy path `@regression`.
   - Describir resultados observables por HTTP, no detalles internos.

2. **Crear e2e HTTP** en `tests/e2e/<feature>.e2e.test.ts`.
   - Arrancar `bun run src/main.ts` con `PORT` de test.
   - Esperar `/healthz` sin `sleep` fijo; usar retry con deadline.
   - Enviar `X-Correlation-Id` y validar header de respuesta.
   - Matar el proceso en `afterAll`/`finally`.

3. **Crear integration tests** en `tests/integration/`.
   - Probar adapters in-memory/fallback.
   - No requerir Docker para la suite por defecto.
   - Tests con Valkey/TigerBeetle real deben quedar en script separado o skip explícito si falta infra.

4. **Crear smoke** en `tests/smoke/http.smoke.ts`.
   - Debe aceptar `BASE_URL` si el servicio ya está levantado.
   - Si no hay `BASE_URL`, puede levantar proceso local en `TEST_PORT`.
   - Debe validar sólo el flujo mínimo de demo.

5. **Crear k6 smoke/load** en `tests/k6/<feature>.js`.
   - Usar `BASE_URL` para apuntar a servicio ya levantado.
   - Definir thresholds de `http_req_failed`, `http_req_duration` y `checks`.
   - Mantenerlo chico por defecto (`2 VUs`, `10s`) para demo local.

6. **Actualizar scripts** en `package.json`:
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

7. **Actualizar `scripts/test.sh`** para correr guardrails, build, unit, integration, e2e y smoke.

8. **Registrar el grupo en `.ai/test-groups.yaml`** si la suite debe entrar en `./nx test --list`.
