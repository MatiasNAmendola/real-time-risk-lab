# java-monolith PoC

Single-JVM Vert.x monolith with full functional parity to `poc/java-vertx-distributed`.

## Architecture contrast

| Dimension | java-monolith (this) | java-vertx-distributed |
|---|---|---|
| JVM count | 1 | 4 |
| Event bus | Local (in-process) | Clustered (Hazelcast) |
| Layer communication | Direct method call via event bus | Network hop |
| Port | 8090 | 8080 |
| Performance | No inter-layer network overhead | Network overhead on each hop |
| Blast radius | Full process | Per-layer |
| Independent scaling | No | Yes (per layer) |

## Build

```bash
./gradlew :poc:java-monolith:build
./gradlew :poc:java-monolith:shadowJar
```

## Run standalone

```bash
poc/java-monolith/scripts/run.sh
```

## API

All endpoints are identical to java-vertx-distributed, served on port **8090**:

- `POST /risk` — evaluate transaction
- `GET /risk/stream` — SSE push
- `WS /ws/risk` — bidirectional WebSocket
- `POST /webhooks` — subscribe
- `GET/DELETE /webhooks/{id}` — manage subscriptions
- `GET /admin/rules` + `POST /admin/rules/reload` — backoffice (X-Admin-Token)
- `GET /healthz`, `GET /readyz`
- `GET /openapi.json`, `GET /docs`

## Example

```bash
curl -s -X POST http://localhost:8090/risk \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-001","customerId":"c-1","amountCents":200000}' | jq .
```

Expected response:
```json
{
  "decision": "DECLINE",
  "reason": "HighAmountRule v1: amount 200000 >= 50000 threshold",
  "correlationId": "..."
}
```

## Tests

```bash
# Unit tests (no infra required)
./gradlew :poc:java-monolith:test

# Integration tests (requires Docker)
./gradlew :poc:java-monolith:test -Pintegration

# ATDD (requires compose stack running)
./gradlew :poc:java-monolith:atdd-tests:test -Patdd
```
