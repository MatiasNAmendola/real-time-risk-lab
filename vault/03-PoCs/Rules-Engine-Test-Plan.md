---
title: Rules Engine Test Plan — cobertura de tests del motor de reglas
tags: [poc, testing, rules-engine, atdd, benchmark, junit]
created: 2026-05-12
source_archive: docs/20-business-rules-test-plan.md (migrado 2026-05-12)
---

# Rules Engine Test Plan — cobertura de tests del motor de reglas

Este documento guía al implementador del motor de reglas declarativo. Cada sección define los casos de prueba, el input esperado, y el assertion.

---

## 1. Unit tests por tipo de regla

Los unit tests ejercitan el intérprete de cada tipo de regla en aislamiento, con un `FeatureSnapshot` construido manualmente. No hay IO, no hay Spring context, no hay mocks de base de datos.

### 1.1 ThresholdRule — HighAmountRule

Campo: `amountCents`, operator: `>`, value: `10_000_000`.

| Caso | Input `amountCents` | Acción esperada |
|---|---|---|
| [x] UT-THR-01 | 10_000_001 (justo por encima) | DECLINE |
| [x] UT-THR-02 | 10_000_000 (exactamente igual) | ALLOW (no activa `>`) |
| [x] UT-THR-03 | 9_999_999 (justo por debajo) | ALLOW |
| [x] UT-THR-04 | 0 | ALLOW |
| [x] UT-THR-05 | -1 (negativo) | ALLOW |
| [x] UT-THR-06 | null | debe lanzar FieldNotFoundException |

### 1.3 CombinationRule — NewDeviceYoungCustomer

Sub-reglas: `newDevice == true` AND `customerAgeDays < 30`.

| Caso | newDevice | customerAgeDays | ¿Activa esperada? |
|---|---|---|---|
| [x] UT-COM-01 | true | 20 | true (ambas sub-reglas verdaderas) |
| [x] UT-COM-02 | false | 20 | false (newDevice falla) |
| [x] UT-COM-03 | true | 30 | false (customerAgeDays no es < 30) |
| [x] UT-COM-04 | false | 45 | false (ninguna) |

### 1.4 VelocityRule

Campo: `transactionCount10m`, count: 5.

| Caso | transactionCount10m | ¿Activa esperada? |
|---|---|---|
| [x] UT-VEL-01 | 4 | false |
| [x] UT-VEL-02 | 5 | true |
| [x] UT-VEL-03 | 6 | true |
| [x] UT-VEL-04 | 0 | false |

---

## 2. Engine tests

### 2.2 Evaluación básica

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
| [x] ENG-17 | 10 threads evaluando en loop; mid-stream swap AtomicReference de v1 a v2 | Cada thread completa con la versión que leyó al inicio; cero NullPointerException; cero versión mixta dentro de un mismo request |

---

## 3. Escenarios ATDD (Karate + Cucumber)

Archivo: `tests/risk-engine-atdd/src/test/resources/features/12_backoffice_rules.feature`

### Escenario 1: Risk admin baja threshold y decision cambia

```gherkin
Feature: Backoffice rule management
  Scenario: Risk admin lowers threshold and decision changes
    When admin sends PUT /admin/rules/HighAmountRule with value 5000000
    And admin sends POST /admin/rules/reload
    And POST /risk with body { amountCents: 7500000, customerId: "cust_test" }
    Then the response status is 200
    And the decision is "DECLINE"
    And triggeredRules contains "HighAmountRule"
    And rulesVersionHash matches the hash after the reload
```

### Escenario 4: Config inválido es rechazado

```gherkin
  Scenario: Broken config is rejected and previous config remains active
    When admin attempts to load v3-broken/rules.yaml via POST /admin/rules/reload
    Then the response status is 400
    And the response body contains 3 validation errors
    And POST /risk still returns decisions using v2 rules
```

### Escenario 5: Reconstrucción de decisión auditada

```gherkin
  Scenario: Auditor reconstructs a past decision with exact rule version
    When GET /admin/rules/version/sha256:abc123...
    Then the response contains the full RuleConfig snapshot
    And POST /admin/rules/test with { config_hash: "sha256:abc123...", transaction: <original> }
    Then the dry-run decision matches the original decision
    And no audit entry is written for the dry-run
```

---

## 4. Performance benchmarks

### Bench-01: Evaluación de 100 reglas vs 1 regla

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
```

---

## Estado de implementación (2026-05-07)

| Categoría | Definidos | Implementados | Estado |
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

## Related

- [[Rules-Engine]] — modelo conceptual y tipos de reglas.
- [[0046-declarative-rules-engine]] — decisiones de diseño.
- [[Backoffice-Audit-Trail]] — API admin y escenarios E2E.
- [[ATDD]] — filosofía de testing acceptance-driven.
- [[Risk-Platform-Overview]]
