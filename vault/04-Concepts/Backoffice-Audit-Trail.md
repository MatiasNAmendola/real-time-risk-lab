---
title: Backoffice Audit Trail — Admin API y gestión de reglas
tags: [concept, pattern/audit-trail, admin-api, rules-engine, compliance, backoffice]
created: 2026-05-12
source_archive: docs/19-backoffice-simulation-design.md (migrado 2026-05-12)
---

# Backoffice Audit Trail — Admin API y gestión de reglas

## Contexto

Este documento describe el API admin que un backoffice de Riesgo consumiría para gestionar el config de reglas en tiempo real. No construimos la UI — construimos el contrato de API, el modelo de datos del audit trail, y los escenarios E2E que prueban el flujo completo.

La simulación reemplaza un operador humano usando la UI: un test HTTP (curl o Karate) actúa como el "Risk admin" que carga reglas, las modifica, y verifica que las decisiones cambian en consecuencia.

---

## API Admin (REST)

Todos los endpoints están bajo el prefijo `/admin/rules`. Requieren autenticación.

### Endpoints

#### Listar reglas activas

```
GET /admin/rules
```

Response:
```json
{
  "version": "1.0.0",
  "hash": "sha256:abc123...",
  "deployed_at": "2026-05-07T20:00:00Z",
  "deployed_by": "risk.team@example.com",
  "rules": [...],
  "total": 8,
  "enabled_count": 7
}
```

#### Detalle de una regla

```
GET /admin/rules/{name}
```

#### Crear regla

```
POST /admin/rules
Content-Type: application/json

{
  "name": "KnownFraudDevice",
  "type": "device_fingerprint",
  "enabled": true,
  "action": "DECLINE",
  "weight": 1.0,
  "parameters": { "lookup": "fraud_device_table" }
}
```

#### Actualizar regla

```
PUT /admin/rules/{name}
```

Solo se permite actualizar campos de la regla (no el nombre ni el tipo). El servidor registra el diff `before/after` en el audit log.

#### Desactivar regla

```
DELETE /admin/rules/{name}
```

No borra la regla del config. La marca `enabled: false` y agrega una entrada al audit log. Para borrado permanente: `DELETE /admin/rules/{name}?permanent=true` con header `X-Confirm-Delete: yes`.

#### Forzar reload

```
POST /admin/rules/reload
```

Response:
```json
{
  "previous_hash": "sha256:abc123...",
  "new_hash": "sha256:def456...",
  "rules_loaded": 8,
  "reload_duration_ms": 12
}
```

#### Historial de cambios (audit)

```
GET /admin/rules/audit?from=2026-05-01T00:00:00Z&to=2026-05-07T23:59:59Z&rule=HighAmountRule
```

#### Dry-run / test

```
POST /admin/rules/test
Content-Type: application/json

{
  "transaction": { "amountCents": 7500000, ... },
  "config_hash": "sha256:def456..."   // opcional: evaluar contra una version especifica
}
```

Fundamental para que Riesgo valide una nueva regla antes de activarla en prod. No registra en el audit log.

---

## Autenticación

### PoC

Header `X-Admin-Token: <token>` validado contra AWS Secrets Manager (Floci en tests/staging, ADR-0042).

### Producción

OAuth2 + RBAC via Keycloak:
- Scope `rules:read` para `GET` endpoints.
- Scope `rules:write` para `POST`, `PUT`, `DELETE`.
- Scope `rules:admin` para reload y dry-run.

---

## Audit Trail

### Modelo de datos

Tabla `rules_audit_log`:

```sql
CREATE TABLE rules_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(20) NOT NULL,   -- create|update|disable|delete|reload|test
    rule_name       VARCHAR(100),
    before_json     JSONB,
    after_json      JSONB,
    actor           VARCHAR(255) NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID NOT NULL,
    reason          TEXT,
    config_hash_before VARCHAR(64),
    config_hash_after  VARCHAR(64)
);
```

### Dual write: DB + Kafka

Cada operación de escritura hace dual write:
1. Persiste en `rules_audit_log`.
2. Emite un evento en el topic Kafka `rules-audit`.

Esto permite que sistemas downstream (alertas, dashboards, compliance reporting) reaccionen a cambios de reglas en tiempo real sin polling.

### Reproducibilidad de decisiones

Para auditar una decisión vieja:

```
GET /admin/rules/audit?correlation_id=<decision_correlation_id>
# Obtener rules_version_hash

GET /admin/rules/version/{hash}
# Obtener snapshot completo del config en ese momento
```

---

## Tests E2E del flujo backoffice

### Escenario 1: Threshold más bajo → decision cambia

1. `POST /risk` con `amountCents = 7_500_000` → esperar `APPROVE`.
2. Admin hace `PUT /admin/rules/HighAmountRule` con `value = 5_000_000`.
3. `POST /admin/rules/reload`.
4. `POST /risk` con el mismo request → esperar `DECLINE`.
5. `GET /admin/rules/audit?rule=HighAmountRule` → verificar cambio con diff before/after.

### Escenario 4: Hot reload sin downtime

1. Iniciar carga: 50 TPS durante 30 segundos.
2. A los 15 segundos, hacer `PUT` en `HighAmountRule` + `POST /admin/rules/reload`.
3. Verificar: cero requests fallaron (no 5xx), p99 no aumentó más de 20ms, decisions antes del swap → v1 hash, decisions después del swap → v2 hash.

**Mecanismo**: el `AtomicReference<RuleConfig>` garantiza que cada request que comienza a evaluar lee una versión completa. El swap es atómico.

### Escenario 6: Reconstrucción de decisión auditada

1. `GET /admin/rules/audit?rule_version_hash=sha256:abc123...` → obtener snapshot.
2. `POST /admin/rules/test` con `config_hash = "sha256:abc123..."` y los parámetros de la transacción original.
3. Verificar que la decisión reproducida coincide con la decisión original.

Este escenario es el más crítico para compliance.

---

## Consideraciones de seguridad del admin API

1. **Rate limiting**: el endpoint `POST /admin/rules/reload` debe tener rate limit estricto (ej. 1 por minuto).
2. **Dry-run no modifica estado**: el endpoint `/test` no debe escribir en el audit log ni disparar eventos Kafka.
3. **Validación de schema al write**: todo `POST` o `PUT` valida el schema completo antes de persistir.
4. **Separación de ambientes**: el header `deployment_env` previene que un YAML de dev sea cargado en prod.

---

## Key Design Principle

> El audit trail no es un nice-to-have. Es lo que separa un sistema que el regulador acepta de uno que no. Cada decisión tiene que poder ser justificada con la versión exacta de reglas que se usó, semanas o meses después. Esa es la diferencia entre un motor de reglas y un sistema de fraude operable.

## Related

- [[Rules-Engine]] — modelo conceptual y tipos de reglas.
- [[0046-declarative-rules-engine]] — decisiones de diseño del engine.
- [[Rules-Engine-Test-Plan]] — plan de tests del engine.
- [[Correlation-ID-Propagation]] — trazabilidad end-to-end.
- [[Risk-Platform-Overview]]
