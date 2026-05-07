# Rules Config — Samples

Samples de configuracion YAML para el motor de reglas declarativo del Risk Engine.
Cada version representa un estado del config que el equipo de Riesgo deployaria via el admin API.

## Estructura de directorios

```
examples/rules-config/
  v1/rules.yaml         Config inicial (8 reglas)
  v2/rules.yaml         Config con 3 cambios sobre v1 (9 reglas)
  v3-broken/rules.yaml  Config invalido — para tests de validacion
  README.md             Este archivo
```

---

## Version 1 — Estado inicial

**Archivo**: `v1/rules.yaml`
**Version**: `1.0.0`
**Hash esperado**: `sha256:abc123...` (calculado por el engine al cargar)
**Reglas**: 8 (7 enabled, 1 disabled)

| Regla | Tipo | Action | Enabled |
|---|---|---|---|
| HighAmountRule | threshold | DECLINE | true |
| NewDeviceYoungCustomer | combination | REVIEW | true |
| VelocityHigh | velocity | REVIEW | true |
| ChargebackHistory | threshold | DECLINE | true |
| InternationalRestricted | international | REVIEW | false |
| WeekendNight | time_of_day | FLAG | true |
| HighRiskMerchant | merchant_category | REVIEW | true |
| TrustedCustomerAllowlist | allowlist | ALLOW | true |

**Aggregation policy**: `worst_case_with_allowlist_override`
**Timeout**: 50ms; fallback: REVIEW

---

## Version 2 — Q2 2026 tightening

**Archivo**: `v2/rules.yaml`
**Version**: `2.0.0`
**Hash esperado**: `sha256:def456...`
**Reglas**: 9 (8 enabled, 1 disabled)

### Diff respecto a v1

| Campo | v1 | v2 | Motivo |
|---|---|---|---|
| `HighAmountRule.parameters.value` | 10000000 ($100k) | 5000000 ($50k) | Incremento de fraude 15% en rango $50k-$100k (abril 2026, ref RA-2026-042) |
| `WeekendNight.action` | FLAG | REVIEW | FLAG no genera carga de trabajo en dashboard; REVIEW fuerza atencion manual |
| `KnownFraudDevice` (nueva) | — | DECLINE | Tres fingerprints confirmados en incidente FRD-2026-019 |

**Efecto observable en tests**:
- Una transaccion de $75,000 que era APPROVE con v1 pasa a ser DECLINE con v2.
- Una transaccion de sabado nocturno que era FLAG con v1 pasa a ser REVIEW con v2.
- Un device con fingerprint `fp_abc123malicious` que era APPROVE con v1 pasa a ser DECLINE con v2.

---

## Version 3-broken — Config invalido (test de validacion)

**Archivo**: `v3-broken/rules.yaml`
**Version**: `3.0.0-broken`

Este archivo contiene 3 errores intencionales para testear la validacion del engine:

| Error | Regla | Campo | Descripcion |
|---|---|---|---|
| ERROR-01 | UnknownFraudSignal | type | Tipo `magic_oracle` no existe en el enum |
| ERROR-02 | BrokenThreshold | parameters | Parameters vacio — faltan `field`, `operator`, `value` |
| ERROR-03 | InvalidVelocity | parameters.windowMinutes | Valor negativo (-5) invalido |

**Comportamiento esperado**: el engine rechaza el load completo con HTTP 400. El config activo (v2) se mantiene sin cambios.

---

## Como cargar una version

### PoC (cuando este implementado)

```bash
# Copiar el archivo a la ruta monitoreada por el engine
cp examples/rules-config/v2/rules.yaml /path/to/config/rules.yaml

# O forzar reload via admin API
curl -X POST http://localhost:8080/admin/rules/reload \
  -H "X-Admin-Token: dev-token-local"
```

### Verificar version activa

```bash
curl http://localhost:8080/admin/rules/version \
  -H "X-Admin-Token: dev-token-local"
```

### Dry-run antes de cargar

```bash
curl -X POST http://localhost:8080/admin/rules/test \
  -H "X-Admin-Token: dev-token-local" \
  -H "Content-Type: application/json" \
  -d '{
    "transaction": {
      "amountCents": 7500000,
      "customerId": "cust_test_001",
      "merchantMcc": "5411",
      "newDevice": false,
      "customerAgeDays": 365
    }
  }'
```

El dry-run evalua la transaccion contra el config activo sin registrar la decision ni emitir eventos.

---

## Invariantes del schema

Cualquier YAML de reglas valido debe cumplir:

1. `version` es un semver valido.
2. `aggregation_policy` es uno de: `worst_case_with_allowlist_override`, `first_match`, `weighted_score`.
3. `timeout_ms` es entero positivo.
4. `fallback_decision` es uno de: `DECLINE`, `REVIEW`, `FLAG`, `ALLOW`.
5. Cada regla tiene `name` unico dentro del config.
6. `type` es uno de los 9 tipos soportados.
7. `parameters` no puede ser vacio para tipos que requieren campos (threshold, velocity, combination, etc.).
8. `weight` es float positivo.
9. `action` es uno de: `DECLINE`, `REVIEW`, `FLAG`, `ALLOW`.
10. Valores numericos en `parameters` cumplen sus restricciones (eg. `windowMinutes > 0`, `count > 0`).

Una violacion de cualquier invariante hace que el load falle completo. El engine no acepta configs parcialmente validos.
