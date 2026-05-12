---
title: Rules Engine — modelo conceptual y tipos de reglas
tags: [concept, pattern/rules-engine, fraud, declarative, domain]
created: 2026-05-12
source_archive: docs/18-rules-engine-design.md (migrado 2026-05-12, parte de concepto)
---

# Rules Engine — modelo conceptual y tipos de reglas

Un motor de reglas declarativo interpreta una configuración externa (YAML, ConfigMap, AppConfig) en lugar de codificar reglas en Java. Riesgo define la política; IT construye el intérprete.

## Modelo conceptual

```
Rule {
  name:       string          -- identificador unico
  version:    semver          -- version de esta regla (no del config global)
  enabled:    bool            -- toggle sin borrar
  type:       RuleType        -- determina el interprete a usar
  parameters: object          -- especificos al tipo
  weight:     float           -- para scoring ponderado
  action:     RuleAction      -- DECLINE | REVIEW | FLAG | ALLOW
  metadata: {
    owner:              string   -- equipo duenio
    created_at:         iso8601
    last_modified_by:   string
    last_modified_at:   iso8601
    deployment_env:     string[] -- ["dev","staging","prod"]
    audit_trail:        AuditEntry[]
  }
}

RuleType     = "threshold" | "combination" | "velocity" | "chargeback_history"
             | "international" | "time_of_day" | "merchant_category"
             | "device_fingerprint" | "allowlist"

RuleAction   = "DECLINE" | "REVIEW" | "FLAG" | "ALLOW"

RuleConfig {
  version:              semver
  hash:                 string      -- sha256 del contenido canonico
  deployed_at:          iso8601
  deployed_by:          string
  environment:          string
  aggregation_policy:   AggregationPolicy
  timeout_ms:           int
  fallback_decision:    RuleAction
  rules:                Rule[]
  audit:                AuditEntry[]
}
```

La separacion entre `RuleConfig.version` y `Rule.version` permite rastrear que una regla especifica cambio sin asumir que todo el archivo fue modificado.

## Tipos de reglas soportados

### 1. `threshold`

Evalua un campo numerico del request contra un valor con un operador.

```
parameters:
  field:    string     -- path en RiskRequest o FeatureSnapshot
  operator: ">" | ">=" | "<" | "<=" | "==" | "!="
  value:    number
```

Ejemplo: `HighAmountRule` — DECLINE si `amountCents > 10_000_000`.

### 2. `combination`

Combina N sub-reglas inline. Cada sub-regla puede ser threshold o boolean.

```
parameters:
  requireAll: bool        -- AND si true, OR si false
  subrules:
    - type: "boolean" | "threshold"
      field: string
      equals: bool        -- para boolean
      operator: string    -- para threshold
      value: any
```

Ejemplo: `NewDeviceYoungCustomer` — REVIEW si dispositivo nuevo AND cuenta < 30 dias.

### 3. `velocity`

Cuenta eventos en una ventana de tiempo deslizante para un grupo (customerId, deviceId, ip).

```
parameters:
  count:         int      -- maximo permitido
  windowMinutes: int      -- tamano de la ventana
  groupBy:       string   -- campo de agrupacion
```

Nota de implementacion: requiere acceso a un store de ventana (Redis sorted set por score=timestamp). El interprete no lee directamente Redis; lee el campo pre-computado en `FeatureSnapshot`.

### 4. `chargeback_history`

Especializado en threshold sobre campo de historial de chargebacks.

```
parameters:
  field:     string   -- "chargebackCount90d" | "chargebackCount30d"
  threshold: int
  operator:  ">=" | ">"
```

### 5. `international`

Evalua si el pais del comercio o del issuer esta en una lista de paises restringidos.

```
parameters:
  restrictedCountries: string[]   -- ISO 3166-1 alpha-2
  action:              RuleAction
```

### 6. `time_of_day`

Evalua si la transaccion ocurre dentro de una ventana horaria en determinados dias.

```
parameters:
  hoursFrom: int        -- hora inicio (UTC o local segun config)
  hoursTo:   int        -- hora fin (puede cruzar medianoche)
  days:      string[]   -- ["MONDAY"..."SUNDAY"]
```

Cruce de medianoche: el interprete trata la ventana como `hour >= hoursFrom OR hour < hoursTo`.

### 7. `merchant_category`

Evalua si el MCC de la transaccion esta en una lista de categorias de alto riesgo.

```
parameters:
  mccCodes: string[]   -- lista de MCC de 4 digitos
```

### 8. `device_fingerprint`

Compara el fingerprint del dispositivo contra una denylist.

```
parameters:
  denyList: string[]           -- fingerprints inline (uso en dev/test)
  lookup:   string | null      -- nombre de tabla/cache si es lookup dinamico
```

### 9. `allowlist`

Override que permite a clientes de confianza bypassar todas las demas reglas.

```
parameters:
  customerIds: string[] | null  -- lista inline
  lookup:      string | null    -- nombre de tabla
  override:    bool             -- si true, cortocircuita la evaluacion
```

## Versionado y rollback

Cada `RuleConfig` cargado en memoria tiene `hash` (sha256 del contenido canonico), `deployed_at` y `deployed_by`. Cada decision evaluada incluye `rules_version_hash` en el response y en el evento de auditoria.

Invariante: nunca se borra una version anterior del config. Se puede archivar, pero la historia tiene que ser recuperable.

## Related

- [[0046-declarative-rules-engine]] — decisiones de diseño: aggregation policy, storage, hot reload.
- [[Barrier-Shadow-Circuit-Modes]] — modos de operación del engine.
- [[Backoffice-Audit-Trail]] — API admin y audit trail para gestión de reglas.
- [[Idempotency]]
- [[Risk-Platform-Overview]]
