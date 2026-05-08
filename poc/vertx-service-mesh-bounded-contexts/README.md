# vertx-service-mesh-bounded-contexts

PoC dedicado para demostrar **service-to-service real** entre bounded contexts, sin confundirlo con `vertx-layer-as-pod-eventbus`.

## Qué demuestra

```
[risk-decision-service HTTP] --EventBus RPC--> [fraud-rules-service]
             |              --EventBus RPC--> [ml-scorer-service]
             └-------------- EventBus async -> [audit-service]
```

- `risk-decision-service`: entrypoint HTTP `/risk`, budget p99-oriented y composición de respuestas.
- `fraud-rules-service`: bounded context propietario de reglas versionadas.
- `ml-scorer-service`: bounded context propietario del scoring, con timeout/fallback explícito.
- `audit-service`: downstream async, no bloquea la decisión.

Esto contrasta con `vertx-layer-as-pod-eventbus`, que sigue siendo **layer-as-pod**: controller/usecase/repository/consumer son capas de un mismo bounded context.

## Cómo correr

```bash
./scripts/up.sh
./scripts/demo.sh
./scripts/down.sh
```

Endpoint:

```bash
curl -fsS -H 'Content-Type: application/json' \
  -d '{"transactionId":"tx1","customerId":"c1","amountCents":125000,"newDevice":true}' \
  http://localhost:8090/risk | jq .
```

## Saturación / timeouts

- `scripts/up.sh` tiene timeout duro (`TIMEOUT_SECONDS`, default 120s).
- Si detecta containers en `Restarting`, corta y expone `docker compose ps` + logs.
- `risk-decision-service` usa `DeliveryOptions.setSendTimeout(120ms)` por llamada sync.
- ML falla a fallback (`MlScoreResult(..., fallback=true)`); reglas fallan cerrado con 502.
- Para diagnóstico global del repo: `../../scripts/diagnose-saturation.sh`.

## Estado

En progreso: PoC mínima compilable para discusión técnica; siguiente paso natural es agregar k6 comparando HTTP/JSON inter-service vs EventBus inter-service.
