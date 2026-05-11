# 35 — Runbook: cuando el demo falla

> Cuando `./nx demo rest` no devuelve HTTP 200, el síntoma puede ser de 5 categorías. Este runbook navega cada una en orden de probabilidad. Cada paso tiene un comando de probe + el fix más común.

## 0. Triage — ¿qué tipo de error?

```bash
./nx demo rest --amount 150000 --customer cust-001 2>&1 | tail -5
```

| Output | Categoría | Ir a |
|---|---|---|
| `curl: (7) Failed to connect` | Apps no levantadas | §1 |
| `HTTP 502 ... Timed out reply` | Cluster Vert.x roto | §2 |
| `HTTP 401` | Auth bloqueando | §3 |
| `HTTP 4xx ... validation` | Payload inválido | §4 |
| `HTTP 500` | App crash interno | §5 |

## 1. Apps no responden

Verificá compose:
```bash
docker compose -f compose/docker-compose.yml -f poc/vertx-layer-as-pod-eventbus/compose.override.yml ps
```

- **Container "Created" o "Exited"** → init container falló. Ver §1.1.
- **Container "Up (unhealthy)"** → healthcheck mismatch. Ver §1.2.
- **No containers** → `./nx up vertx-layer-as-pod-eventbus` no se ejecutó o falló. Re-corré.

### 1.1 Init containers

```bash
docker logs compose-moto-init-1
docker logs compose-elasticmq-init-1
docker logs compose-postgres-init-1
docker logs compose-redpanda-init-1
docker logs compose-openbao-init-1
docker logs compose-minio-init-1
```

Buscá `error`, `exit 1`, `connection refused`. Si moto-init falló: race conocido (ver `docs/34-lessons-learned.md` §5).

### 1.2 Healthcheck mismatch

```bash
docker inspect compose-controller-app-1 --format '{{json .State.Health}}' | jq .Log[-1]
```

Si falla con `path not found` → el healthcheck del compose apunta a path equivocado. App podría estar OK pero el compose lo marca unhealthy.

## 2. HTTP 502 — Vert.x EventBus timeout

Síntoma típico:
```json
{"error":"Timed out after waiting 15000(ms) for a reply. address: __vertx.reply.<uuid>, repliedAddress: usecase.evaluate"}
```

### 2.1 Cluster Hazelcast formado?

```bash
docker logs compose-controller-app-1 2>&1 | grep "Members {" | tail -3
```

Esperás `size:3` (controller + usecase + repository). Si <3, los pods no se ven entre sí (red, DNS, firewall).

### 2.2 EventBus host advertisement

Bug conocido (ADR-0039): si el container no tiene `EVENT_BUS_HOST` configurado, advierte hostname random.

```bash
docker exec compose-usecase-app-1 sh -c 'cat /proc/1/environ | tr "\0" "\n" | grep EVENT_BUS_HOST'
```

Esperás `EVENT_BUS_HOST=usecase-app`. Si no, falta env var en el compose.

### 2.3 Verticle deployado y consumer registrado?

```bash
docker logs compose-usecase-app-1 2>&1 | grep -E "Verticle deployed|consumer registered" | tail -5
```

Esperás `EvaluateRiskVerticle deployed: 1` y similar.

### 2.4 BlockedThreadChecker warnings?

```bash
docker logs compose-usecase-app-1 2>&1 | grep "BlockedThreadChecker" | tail -3
```

Si dispara repetido → AWS SDK init en event loop (fix en 9i, ver `docs/34-lessons-learned.md` §2).

## 3. HTTP 401

Endpoint admin protegido. Necesitás token válido.
```bash
./nx admin rules list  # sin auth, vas a 401
# Get token from Floci Secrets Manager (ADR-0042):
aws --endpoint-url http://localhost:4566 secretsmanager get-secret-value \
  --secret-id admin/token --query SecretString --output text
```

## 4. HTTP 4xx — payload validation

DTO autoritativo: `poc/vertx-layer-as-pod-eventbus/shared/src/main/java/io/riskplatform/distributed/shared/RiskRequest.java` (ADR-… verás referencia en docs/34-lessons-learned.md).

Campos requeridos: `transactionId`, `customerId`, `amountCents`, `correlationId`, `idempotencyKey`. Cualquier extra es ignorado.

```bash
curl -X POST http://localhost:8080/risk -H "Content-Type: application/json" -d '{
  "transactionId": "tx-test-1",
  "customerId": "cust-001",
  "amountCents": 150000,
  "correlationId": "corr-1",
  "idempotencyKey": "idem-1"
}' -w "\n%{http_code}\n"
```

## 5. HTTP 500 — App crash

```bash
docker logs compose-usecase-app-1 2>&1 | grep -E "Exception|ERROR" | tail -10
```

Buscá:
- `SecretsManager` errors → floci-init no creó secrets (ver §1.1).
- `S3` / `Sqs` errors → Floci sin recursos (ver §1.1).
- `OutOfMemoryError` → bumpear `mem_limit` en compose.override.yml.
- `Hazelcast` partition errors → cluster flapping bajo memoria justa (ver `docs/34-lessons-learned.md` §6).

## 6. Service-by-service connectivity probe

Helper: corré el patrón del service-connectivity agent.

```bash
# Postgres
docker exec compose-postgres-1 psql -U risk_user -d risk_db -c "\dt"

# Valkey
docker exec compose-valkey-1 valkey-cli PING

# Redpanda
docker exec compose-redpanda-1 rpk topic list

# Floci — S3 buckets
aws --endpoint-url http://localhost:4566 s3 ls

# Floci — SQS queues
aws --endpoint-url http://localhost:4566 sqs list-queues

# Floci — Secrets Manager
aws --endpoint-url http://localhost:4566 secretsmanager list-secrets

# Floci — health
curl -s http://localhost:4566/_floci/health
```

Si alguno falla → ese servicio es la raíz.

## 7. Rebuild + restart si nada funciona

```bash
./nx down vertx
./gradlew clean :poc:vertx-layer-as-pod-eventbus:build -x test
docker compose -f compose/docker-compose.yml -f poc/vertx-layer-as-pod-eventbus/compose.override.yml build
./nx up vertx-layer-as-pod-eventbus
# esperar 60s
./nx demo rest --amount 150000 --customer cust-001
```

## 8. Bundle forensic

Si necesitás compartir contexto:
```bash
./nx debug snapshot
# zip en out/debug/<ts>/
```

## Referencias

- `docs/34-lessons-learned.md` — gotchas no obvios.
- `vault/02-Decisions/0039-vertx-eventbus-host-advertisement.md` — ADR del fix EventBus.
- `out/e2e-verification/<ts>/summary.md` — historial de runs.
