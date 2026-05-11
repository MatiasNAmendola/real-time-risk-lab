# 19 — Backoffice Simulation: Admin API + Audit Trail

## Contexto

Este documento describe el API admin que un backoffice de Riesgo consumiria para gestionar el config de reglas en tiempo real. No construimos la UI — construimos el contrato de API, el modelo de datos del audit trail, y los escenarios E2E que prueban el flujo completo.

La simulacion reemplaza un operador humano usando la UI: un test HTTP (curl o Karate) actua como el "Risk admin" que carga reglas, las modifica, y verifica que las decisiones cambian en consecuencia.

---

## API Admin (REST)

Todos los endpoints estan bajo el prefijo `/admin/rules`. Requieren autenticacion (ver seccion Auth).

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
  "rules": [
    {
      "name": "HighAmountRule",
      "version": "v1",
      "type": "threshold",
      "enabled": true,
      "action": "DECLINE",
      "weight": 1.0
    }
  ],
  "total": 8,
  "enabled_count": 7
}
```

#### Detalle de una regla

```
GET /admin/rules/{name}
```

Devuelve la regla completa incluyendo `parameters` y `metadata`.

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
  "parameters": {
    "lookup": "fraud_device_table"
  }
}
```

El servidor valida el schema, genera `metadata.created_at` y escribe en el audit log. Si la validacion falla (tipo desconocido, parametros faltantes), devuelve `400` con detalle del error.

#### Actualizar regla

```
PUT /admin/rules/{name}
Content-Type: application/json

{
  "parameters": {
    "field": "amountCents",
    "operator": ">",
    "value": 5000000
  }
}
```

Solo se permite actualizar campos de la regla (no el nombre ni el tipo). El servidor registra el diff `before/after` en el audit log.

#### Desactivar regla

```
DELETE /admin/rules/{name}
```

No borra la regla del config. La marca `enabled: false` y agrega una entrada al audit log con `action: "disable"`. Esto preserva la historia y permite re-activar.

Para borrado permanente (infrecuente), se usa `DELETE /admin/rules/{name}?permanent=true` con confirmacion adicional (requiere el header `X-Confirm-Delete: yes`).

#### Forzar reload

```
POST /admin/rules/reload
```

Fuerza al engine a re-leer el config desde la fuente (disco, ConfigMap, AppConfig) independientemente de si el file watch lo detecto. Util para:
- Deploy manual en entornos sin file watch.
- Recuperacion tras un error de watch.

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

Query params opcionales: `from`, `to`, `rule`, `actor`, `action`.

Response: lista de `AuditEntry` paginada.

#### Version actual

```
GET /admin/rules/version
```

Response:
```json
{
  "version": "1.0.0",
  "hash": "sha256:abc123...",
  "deployed_at": "2026-05-07T20:00:00Z",
  "deployed_by": "risk.team@example.com",
  "rules_count": 8,
  "enabled_count": 7
}
```

#### Dry-run / test

```
POST /admin/rules/test
Content-Type: application/json

{
  "transaction": {
    "amountCents": 7500000,
    "customerId": "cust_123",
    "merchantMcc": "7995",
    "newDevice": false,
    "customerAgeDays": 45
  },
  "config_hash": "sha256:def456..."   // opcional: evaluar contra una version especifica
}
```

Evalua la transaccion contra el config activo (o uno especifico por hash) sin registrar la decision. Devuelve que reglas dispararon, cual fue el resultado, y el tiempo de evaluacion. Fundamental para que Riesgo valide una nueva regla antes de activarla en prod.

---

## Autenticacion

### PoC

Header `X-Admin-Token: <token>` validado contra un secret en AWS Secrets Manager (Floci en tests/staging locales, ADR-0042; AWS real en producción).

```java
@Component
class AdminTokenFilter implements OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String token = req.getHeader("X-Admin-Token");
        if (!secretsManager.isValidAdminToken(token)) {
            response.sendError(401, "Invalid admin token");
            return;
        }
        filterChain.doFilter(req, response);
    }
}
```

### Produccion

OAuth2 + RBAC via Keycloak (o equivalente):
- Scope `rules:read` para `GET` endpoints.
- Scope `rules:write` para `POST`, `PUT`, `DELETE`.
- Scope `rules:admin` para reload y dry-run.

El token JWT incluye `sub` (actor) que se persiste en el audit log.

---

## Audit Trail

### Modelo de datos

Tabla `rules_audit_log`:

```sql
CREATE TABLE rules_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(20) NOT NULL,   -- create|update|disable|delete|reload|test
    rule_name       VARCHAR(100),           -- null para acciones de config (reload)
    before_json     JSONB,                  -- estado antes del cambio
    after_json      JSONB,                  -- estado despues del cambio
    actor           VARCHAR(255) NOT NULL,  -- email o service account
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID NOT NULL,          -- para trazar request HTTP -> audit entry
    reason          TEXT,                   -- campo opcional para justificar el cambio
    config_hash_before VARCHAR(64),
    config_hash_after  VARCHAR(64)
);
```

### Dual write: DB + Kafka

Cada operacion de escritura en el admin API hace dual write:
1. Persiste en `rules_audit_log`.
2. Emite un evento en el topic Kafka `rules-audit`.

```json
{
  "event_type": "RuleUpdated",
  "correlation_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-05-07T20:15:00Z",
  "actor": "ana.gomez@example.com",
  "rule_name": "HighAmountRule",
  "action": "update",
  "before": { "parameters": { "value": 10000000 } },
  "after": { "parameters": { "value": 5000000 } },
  "reason": "Q2 tightening — fraud rate increased 15% in $50k-$100k range",
  "config_hash_after": "sha256:def456..."
}
```

El topic Kafka permite que sistemas downstream (alertas, dashboards, compliance reporting) reaccionen a cambios de reglas en tiempo real sin polling.

### Reproducibilidad de decisiones

Para auditar una decision vieja:

```
GET /admin/rules/audit?correlation_id=<decision_correlation_id>
```

Devuelve el `rules_version_hash` de la decision. Con ese hash:

```
GET /admin/rules/version/{hash}
```

Devuelve el snapshot completo de la config tal como estaba en ese momento (si se archivan los snapshots — ver seccion de versionado en doc 18).

---

## Tests E2E del flujo backoffice

Los tests simulan un operador de Riesgo usando el admin API para modificar reglas y verifican que las decisiones de fraude cambian en consecuencia.

### Escenario 1: Threshold mas bajo → decision cambia

**Setup**: config en v1 con `HighAmountRule.value = 10_000_000` ($100k).

**Steps**:
1. `POST /risk` con `amountCents = 7_500_000` ($75k) → esperar `APPROVE` (por debajo del threshold).
2. Admin hace `PUT /admin/rules/HighAmountRule` con `value = 5_000_000` ($50k).
3. `POST /admin/rules/reload`.
4. `POST /risk` con el mismo request → esperar `DECLINE`.
5. `GET /admin/rules/audit?rule=HighAmountRule` → verificar que el cambio aparece con `before.value = 10000000`, `after.value = 5000000`.

### Escenario 2: Desactivar regla → decision cambia

**Setup**: `WeekendNight` rule enabled, evaluacion en horario de riesgo.

**Steps**:
1. `POST /risk` con timestamp de sabado 23:00 → esperar `FLAG`.
2. Admin hace `DELETE /admin/rules/WeekendNight` (disable).
3. `POST /risk` mismo request → esperar que `WeekendNight` no aparezca en `triggeredRules`.

**Invariante verificado**: la regla persiste en el config con `enabled: false`. Un `GET /admin/rules/WeekendNight` devuelve la regla con ese estado, no 404.

### Escenario 3: Allowlist customer → siempre ALLOW

**Steps**:
1. `POST /risk` con `customerId = "cust_vip_001"` y `amountCents = 20_000_000` → esperar `DECLINE` (HighAmount).
2. Admin hace `PUT /admin/rules/TrustedCustomerAllowlist` agregando `cust_vip_001` a la lista.
3. `POST /risk` mismo request → esperar `ALLOW`.
4. Verificar que el response incluye `overriddenBy: "TrustedCustomerAllowlist"`.

### Escenario 4: Hot reload sin downtime

**Setup**: 1000 transacciones en carga sostenida (load gen con Gatling o Artillery).

**Steps**:
1. Iniciar carga: 50 TPS durante 30 segundos.
2. A los 15 segundos, hacer `PUT` en `HighAmountRule` para bajar threshold + `POST /admin/rules/reload`.
3. Continuar hasta el final de la carga.
4. Verificar:
   - Cero requests fallaron (no 5xx).
   - Latencia p99 no aumento mas de 20ms durante el reload.
   - Decisions antes del swap → version hash v1.
   - Decisions despues del swap → version hash v2.
   - No hay decision con version hash inconsistente (ej. mitad v1, mitad v2 dentro del mismo request).

**Mecanismo**: el `AtomicReference<RuleConfig>` garantiza que cada request que comienza a evaluar lee una version completa. La ventana de "requests con version mixta" es imposible porque el swap es atomico.

### Escenario 5: Rollback automatico por invariante violado

**Setup**: health check que detecta si mas del 95% de transacciones resultan DECLINE.

**Steps**:
1. Admin sube una regla malformada que DECLINE todo (ej. threshold en 0).
2. `POST /admin/rules/reload`.
3. Health check detecta tasa de DECLINE > 95% en ventana de 30 segundos.
4. Sistema hace rollback automatico al hash anterior.
5. `GET /admin/rules/version` → muestra hash de la version anterior.
6. `GET /admin/rules/audit` → muestra entrada con `action: "auto_rollback"`, `reason: "decline_rate_exceeded_threshold"`.

**Invariante del rollback**: el sistema nunca aplica una config que no tiene al menos una version anterior valida para volver.

### Escenario 6: Reconstruccion de decision auditada

**Setup**: decision tomada hace 3 dias con `rules_version_hash = "sha256:abc123..."`.

**Steps**:
1. `GET /admin/rules/audit?rule_version_hash=sha256:abc123...` → obtener snapshot del config.
2. `POST /admin/rules/test` con `config_hash = "sha256:abc123..."` y los parametros de la transaccion original.
3. Verificar que la decision reproducida coincide con la decision original.

Este escenario es el mas critico para compliance: demuestra que el sistema puede justificar una decision vieja con evidencia reproducible.

---

## Consideraciones de seguridad del admin API

1. **Rate limiting**: el endpoint `POST /admin/rules/reload` debe tener rate limit estricto (ej. 1 por minuto) para evitar DOS por reload continuo.

2. **Dry-run no modifica estado**: el endpoint `/test` no debe escribir en el audit log ni disparar eventos Kafka. Si escribe, un analista podria "contaminar" el historial con evaluaciones hipoteticas.

3. **Validacion de schema al write**: todo `POST` o `PUT` valida el schema completo de la regla antes de persistir. Un `type` desconocido o `parameters` faltantes deben devolver `400` con el campo especifico que fallo. Nunca cargar un config invalido en memoria.

4. **Separacion de ambientes**: el header `deployment_env` en el config previene que un YAML de dev sea cargado en prod. El admin API debe verificar que `deployment_env` incluye el ambiente actual del pod.

---

## Key Design Principle

> El audit trail no es un nice-to-have. Es lo que separa un sistema que el regulador acepta de uno que no. Cada decision tiene que poder ser justificada con la version exacta de reglas que se uso, semanas o meses despues. Esa es la diferencia entre un motor de reglas y un sistema de fraude operable.
