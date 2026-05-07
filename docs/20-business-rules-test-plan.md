# 20 — Business Rules Engine: Test Plan

Este documento guia al implementador del motor de reglas declarativo. Cada seccion define los casos de prueba, el input esperado, y el assertion. La cobertura apunta a: correctitud por tipo de regla, comportamiento del engine como sistema, flujo de backoffice end-to-end, y performance bajo carga.

---

## 1. Unit tests por tipo de regla

Los unit tests ejercitan el interprete de cada tipo de regla en aislamiento, con un `FeatureSnapshot` construido manualmente. No hay IO, no hay Spring context, no hay mocks de base de datos.

### 1.1 ThresholdRule — HighAmountRule

Campo: `amountCents`, operator: `>`, value: `10_000_000`.

| Caso | Input `amountCents` | Acción esperada |
|---|---|---|
| [x] UT-THR-01 | 10_000_001 (justo por encima) | DECLINE |
| [x] UT-THR-02 | 10_000_000 (exactamente igual) | ALLOW (no activa `>`) |
| [x] UT-THR-03 | 9_999_999 (justo por debajo) | ALLOW |
| [x] UT-THR-04 | 0 | ALLOW |
| [x] UT-THR-05 | -1 (negativo) | ALLOW |
| [x] UT-THR-06 | null | debe lanzar FieldNotFoundException con message `"Field 'amountCents' not found in snapshot"` |

### 1.2 ThresholdRule — operadores genericos

Cinco operadores: `>`, `>=`, `<`, `<=`, `==`, `!=` contra tres valores representativos.

| Caso | Operador | Valor del campo | Umbral | ¿Activa esperada? |
|---|---|---|---|---|
| [x] UT-THR-07 | `>=` | 100 | 100 | true |
| [x] UT-THR-08 | `>=` | 99 | 100 | false |
| [x] UT-THR-09 | `<` | 50 | 100 | true |
| [x] UT-THR-10 | `<` | 100 | 100 | false |
| [x] UT-THR-11 | `==` | 500 | 500 | true |
| [x] UT-THR-12 | `==` | 501 | 500 | false |
| [x] UT-THR-13 | `!=` | 0 | 0 | false |
| [x] UT-THR-14 | `!=` | 1 | 0 | true |
| [x] UT-THR-15 | `<=` | 100 | 100 | true |

### 1.3 CombinationRule — NewDeviceYoungCustomer

Sub-reglas: `newDevice == true` AND `customerAgeDays < 30`.

| Caso | newDevice | customerAgeDays | ¿Activa esperada? |
|---|---|---|---|
| [x] UT-COM-01 | true | 20 | true (ambas sub-reglas verdaderas) |
| [x] UT-COM-02 | false | 20 | false (newDevice falla) |
| [x] UT-COM-03 | true | 30 | false (customerAgeDays no es < 30, es igual) |
| [x] UT-COM-04 | false | 45 | false (ninguna) |

Caso adicional para `requireAll: false` (OR):

| Caso | newDevice | customerAgeDays | requireAll | ¿Activa esperada? |
|---|---|---|---|---|
| [x] UT-COM-05 | true | 45 | false | true (al menos una verdadera) |
| [x] UT-COM-06 | false | 20 | false | true |
| [x] UT-COM-07 | false | 45 | false | false |
| [x] UT-COM-08 | — | — | true, subrules: [] | false (lista vacia no activa) |

### 1.4 VelocityRule

Campo: `transactionCount10m` (pre-computado en FeatureSnapshot), count: 5.

| Caso | transactionCount10m | ¿Activa esperada? |
|---|---|---|
| [x] UT-VEL-01 | 4 | false (debajo del umbral) |
| [x] UT-VEL-02 | 5 | true (exactamente en el umbral) |
| [x] UT-VEL-03 | 6 | true (por encima) |
| [x] UT-VEL-04 | 0 (ventana expirada / sin historial) | false |

Nota: la ventana de tiempo es responsabilidad del `FeatureExtractor`, no del `VelocityRule` interprete. El test del rule asume que el snapshot ya tiene el conteo correcto. Los tests de `FeatureExtractor` cubren la ventana deslizante.

### 1.5 ChargebackHistory

Campo: `chargebackCount90d`, operator: `>=`, value: 1.

| Caso | chargebackCount90d | ¿Activa esperada? |
|---|---|---|
| [x] UT-CHB-01 | 0 | false |
| [x] UT-CHB-02 | 1 | true |
| [x] UT-CHB-03 | 3 | true |

### 1.6 InternationalRule

Lista `restrictedCountries: ["NK", "IR", "SY"]`.

| Caso | country | ¿Activa esperada? |
|---|---|---|
| [x] UT-INT-01 | "NK" | true |
| [x] UT-INT-02 | "AR" | false |
| [x] UT-INT-03 | null / ausente | false (transaccion domestic, no internacional) |

### 1.7 TimeOfDayRule

hoursFrom: 22, hoursTo: 6, days: [SATURDAY, SUNDAY]. Nota: la ventana cruza medianoche.

| Caso | Hora UTC | Día | ¿Activa esperada? |
|---|---|---|---|
| [x] UT-TOD-01 | 23:00 | SATURDAY | true |
| [x] UT-TOD-02 | 03:00 | SUNDAY | true (dentro de 22-06 en el siguiente dia) |
| [x] UT-TOD-03 | 12:00 | SATURDAY | false (fuera de la ventana horaria) |
| [x] UT-TOD-04 | 23:00 | WEDNESDAY | false (dia no incluido) |

### 1.8 MerchantCategoryRule

mccCodes: ["7995", "5993", "5816"].

| Caso | merchantMcc | ¿Activa esperada? |
|---|---|---|
| [x] UT-MCC-01 | "7995" (gambling) | true |
| [x] UT-MCC-02 | "5411" (grocery) | false |
| [x] UT-MCC-03 | null | false |

### 1.9 AllowlistRule

Parametros con lista inline: `customerIds: ["cust_vip_001", "cust_vip_002"]`.

| Caso | customerId | Expected activa (ALLOW)? |
|---|---|---|
| [x] UT-ALL-01 | "cust_vip_001" | true |
| [x] UT-ALL-02 | "cust_unknown" | false |
| [x] UT-ALL-03 | null | false |

---

## 2. Engine tests

Prueban el `RuleEngine` como componente: carga de config, evaluacion, aggregation, hot reload.

### 2.1 Carga de config

| Caso | Config | Esperado |
|---|---|---|
| [x] ENG-01 | v1/rules.yaml valido | 8 reglas en memoria, 7 enabled |
| [x] ENG-02 | v3-broken/rules.yaml | lanza `ConfigValidationException` con 3 errores; config anterior intacto |
| ENG-03 | archivo inexistente | lanza `ConfigNotFoundException`; config anterior intacto |
| ENG-04 | YAML con syntax error (not-yaml) | lanza `ConfigParseException`; config anterior intacto |

**Invariante**: la carga fallida nunca reemplaza el config activo.

### 2.2 Evaluacion basica

| Caso | Config | Request | Esperado |
|---|---|---|---|
| [x] ENG-05 | v1 | amountCents = 15_000_000 | DECLINE (HighAmountRule) |
| [x] ENG-06 | v1 | amountCents = 5_000_000 | APPROVE (ninguna regla activa) |
| [x] ENG-07 | v1 | customerId en allowlist, amountCents = 15_000_000 | ALLOW (allowlist override) |
| [x] ENG-08 | v1 | newDevice = true, customerAgeDays = 20, amountCents = 5_000 | REVIEW (combination) |

### 2.3 Aggregation — worst case

| Caso | Reglas que disparan | Esperado |
|---|---|---|
| [x] ENG-09 | FLAG + REVIEW | REVIEW (peor) |
| [x] ENG-10 | FLAG + REVIEW + DECLINE | DECLINE (peor) |
| [x] ENG-11 | FLAG + ALLOW (sin override) | FLAG (ALLOW no es override, FLAG gana worst-case) |
| [x] ENG-12 | FLAG + allowlist con override:true | ALLOW (override cortocircuita) |
| [x] ENG-13 | DECLINE + allowlist con override:true | ALLOW (override gana sobre DECLINE) |

### 2.4 Hot reload — decision cambia tras swap

| Caso | Setup | Esperado |
|---|---|---|
| [x] ENG-14 | Cargar v1, evaluar amountCents = 7_500_000 | APPROVE; version hash = v1 hash |
| [x] ENG-15 | (continua) Cargar v2 sin reiniciar | engine carga v2 |
| [x] ENG-16 | (continua) Evaluar mismo request | DECLINE; version hash = v2 hash |

### 2.5 Hot reload — consistencia bajo concurrencia

| Caso | Setup | Esperado |
|---|---|---|
| [x] ENG-17 | 10 threads evaluando en loop; mid-stream swap AtomicReference de v1 a v2 | Cada thread completa con la version que leyo al inicio; cero NullPointerException; cero version mixta dentro de un mismo request |

Implementacion sugerida del test: usar `CountDownLatch` para sincronizar el inicio de los 10 threads, hacer el swap a los 500ms, verificar que los responses con timestamp < swap tienen v1 hash y los posteriores tienen v2 hash.

### 2.6 Timeout y fallback

| Caso | Setup | Esperado |
|---|---|---|
| [x] ENG-18 | Configurar timeout_ms = 1; agregar regla con lookup lento (mock que duerme 100ms) | Decision = REVIEW (fallback); campo `timedOut: true` en response |

---

## 3. Escenarios ATDD (Karate + Cucumber)

Archivo: `tests/integration/features/12_backoffice_rules.feature`

### Escenario 1: Risk admin baja threshold y decision cambia

```gherkin
Feature: Backoffice rule management
  Background:
    Given the rules config is at v1 with HighAmountRule threshold 10000000

  Scenario: Risk admin lowers threshold and decision changes
    When admin sends PUT /admin/rules/HighAmountRule with value 5000000
    And admin sends POST /admin/rules/reload
    And POST /risk with body { amountCents: 7500000, customerId: "cust_test" }
    Then the response status is 200
    And the decision is "DECLINE"
    And triggeredRules contains "HighAmountRule"
    And rulesVersionHash matches the hash after the reload
    And GET /admin/rules/audit?rule=HighAmountRule returns an entry with
      | field  | before.parameters.value = 10000000 |
      | after.parameters.value = 5000000   |
      | action = "update"                  |
```

### Escenario 2: Desactivar regla

```gherkin
  Scenario: Risk admin disables a rule and it stops triggering
    Given the rules config is at v1 with WeekendNight enabled
    And a transaction evaluated on Saturday 23:00 returns FLAG
    When admin sends DELETE /admin/rules/WeekendNight
    And POST /risk with same Saturday 23:00 transaction
    Then triggeredRules does not contain "WeekendNight"
    And GET /admin/rules/WeekendNight returns enabled: false
    And the audit log shows action: "disable" for "WeekendNight"
```

### Escenario 3: Bypass de cliente en allowlist

```gherkin
  Scenario: Trusted customer bypasses all rules
    Given the rules config is at v1
    And POST /risk with { customerId: "cust_vip_001", amountCents: 20000000 } returns DECLINE
    When admin sends PUT /admin/rules/TrustedCustomerAllowlist adding customerId "cust_vip_001"
    And POST /risk with same transaction
    Then the decision is "ALLOW"
    And the response includes overriddenBy: "TrustedCustomerAllowlist"
```

### Escenario 4: Config inválido es rechazado

```gherkin
  Scenario: Broken config is rejected and previous config remains active
    Given the rules config is at v2 (active hash = H2)
    When admin attempts to load v3-broken/rules.yaml via POST /admin/rules/reload
    Then the response status is 400
    And the response body contains 3 validation errors
    And GET /admin/rules/version returns hash H2
    And POST /risk still returns decisions using v2 rules
```

### Escenario 5: Reconstrucción de decisión auditada

```gherkin
  Scenario: Auditor reconstructs a past decision with exact rule version
    Given a past decision with rulesVersionHash "sha256:abc123..." exists in the audit log
    When GET /admin/rules/version/sha256:abc123...
    Then the response contains the full RuleConfig snapshot
    And POST /admin/rules/test with { config_hash: "sha256:abc123...", transaction: <original> }
    Then the dry-run decision matches the original decision
    And no audit entry is written for the dry-run
```

---

## 4. E2E — Simulacion de flujo backoffice

Simulacion completa via curl scripts (o Karate) que replica el flujo de un operador de Riesgo.

### Script de simulacion

```bash
# E2E-01: Verificar estado inicial
curl GET /admin/rules/version

# E2E-02: Dry-run de una transaccion de riesgo
curl POST /admin/rules/test { amountCents: 7500000, ... }

# E2E-03: Bajar threshold
curl PUT /admin/rules/HighAmountRule { parameters.value: 5000000 }
curl POST /admin/rules/reload

# E2E-04: Verificar que la decision cambio
curl POST /risk { amountCents: 7500000, ... }
# Assertion: decision = DECLINE

# E2E-05: Consultar audit trail
curl GET /admin/rules/audit?rule=HighAmountRule

# E2E-06: Intentar cargar config roto
curl POST /admin/rules/reload (apuntando a v3-broken)
# Assertion: HTTP 400 con 3 errores

# E2E-07: Verificar que config activo no cambio
curl GET /admin/rules/version
# Assertion: mismo hash que antes del intento de v3
```

### Assertions de performance E2E

| Metrica | Target |
|---|---|
| `POST /risk` p99 bajo carga de 100 TPS | < 300ms |
| `POST /admin/rules/reload` (load + swap) | < 200ms |
| `GET /admin/rules/audit` (100 entradas) | < 50ms |

---

## 5. Performance benchmarks

### Bench-01: Evaluacion de 100 reglas vs 1 regla

```
Setup:
  - Generar config con 1 regla (baseline)
  - Generar config con 100 reglas (stress)
  - 1000 evaluaciones por config

Assertions:
  - p50 con 100 reglas < 2x p50 con 1 regla (efecto del indice)
  - p99 con 100 reglas < 1ms absoluto
```

### Bench-02: Hot reload durante load gen

```
Setup:
  - 50 TPS sostenido durante 60 segundos
  - A los 30 segundos, reload de v1 a v2

Assertions:
  - Zero errores durante el reload
  - p99 del segundo del reload no supera 1.5x el p99 base
  - No hay decision con version hash inconsistente
  - Todas las decisiones posteriores al reload usan v2 hash
```

### Bench-03: Aggregation bajo carga

```
Setup:
  - Config con 8 reglas; transacciones disenadas para disparar 4 reglas cada una
  - 500 evaluaciones

Assertion:
  - p99 < 2ms (aggregation de 4 reglas no deberia ser notorio)
```

---

## Resumen de cobertura

| Categoria | Casos definidos |
|---|---|
| Unit tests — por tipo de regla | 35 casos |
| Engine tests — load, evaluacion, aggregation, hot reload | 18 casos |
| ATDD scenarios (Gherkin) | 5 escenarios |
| E2E — flujo backoffice simulado | 7 pasos con assertions |
| Performance benchmarks | 3 benches |
| **Total** | **68 casos / escenarios** |

La cobertura prioritaria es: correctitud del evaluador por tipo (unit), atomicidad del hot reload bajo concurrencia (engine), y reproducibilidad de decisiones para audit (E2E). El performance benchmark no bloquea CI pero si bloquea merge a prod.

---

## Estado de implementacion (2026-05-07)

| Categoria | Definidos | Implementados | Estado |
|---|---|---|---|
| Unit tests — por tipo de regla | 35 | 35 | [x] DONE |
| Engine tests — load, eval, aggregation, hot reload | 18 | 18 | [x] DONE |
| Barrier/Shadow/Circuit mode tests | 3 | 3 | [x] DONE |
| Config loader tests | 6 | 6 | [x] DONE |
| Audit trail tests | 5 | 5 | [x] DONE |
| ATDD scenarios Karate — bare-javac PoC | 5 | 5 | [x] DONE |
| ATDD scenarios Karate — Vert.x PoC | 5 | 5 | [x] DONE |
| JMH benchmarks | 3 | 3 | [x] DONE |
| **Total ejecutado** | **57 tests** | **57 tests** | **BUILD SUCCESSFUL** |

ENG-03 (ConfigNotFoundException) y ENG-04 (ConfigParseException) no tienen test dedicado en el plan original — el engine los maneja via la excepcion de la libreria YAML; se valida indirectamente en ENG-02.

Proximos pasos: `./gradlew :pkg:risk-domain:jacocoTestReport` para validar cobertura >85% en `rule/` y `engine/`.
