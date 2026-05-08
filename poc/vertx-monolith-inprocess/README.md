# vertx-monolith-inprocess PoC

Single-JVM Vert.x monolith with full functional parity to `poc/vertx-layer-as-pod-eventbus`.

## Architecture contrast

| Dimension | vertx-monolith-inprocess (this) | vertx-layer-as-pod-eventbus |
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
./gradlew :poc:vertx-monolith-inprocess:build
./gradlew :poc:vertx-monolith-inprocess:shadowJar
```

## Run standalone

```bash
poc/vertx-monolith-inprocess/scripts/run.sh
```

## API

All endpoints are identical to vertx-layer-as-pod-eventbus, served on port **8090**:

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
./gradlew :poc:vertx-monolith-inprocess:test

# Integration tests (requires Docker)
./gradlew :poc:vertx-monolith-inprocess:test -Pintegration

# ATDD (requires compose stack running)
./gradlew :poc:vertx-monolith-inprocess:atdd-tests:test -Patdd
```
