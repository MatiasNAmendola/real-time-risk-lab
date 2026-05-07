---
name: debug-trace-issue
description: Diagnosticar un issue de produccion usando correlation ID, OpenObserve traces y logs
steps: [correlationId, traces, logs, metrics, rootcause, fix]
---

# Workflow: debug-trace-issue

## Cuando usar

Cuando un request falla o tiene latencia anormal y necesitas entender que paso.

## 1. Obtener el correlationId

- Del header de respuesta HTTP: `X-Correlation-Id`.
- De los logs de la app: `grep correlationId=<value> <log-file>`.
- Del payload del evento Kafka: campo `correlationId`.

## 2. Buscar el trace en OpenObserve

```
# Local:
http://localhost:5080

# En k8s:
kubectl port-forward svc/openobserve 5080 -n monitoring
```

1. Ir a "Traces" → buscar por `correlation.id = <value>`.
2. Ver el trace completo: que spans existieron, cuanto duró cada uno.
3. Identificar el span mas largo o el que tiene status ERROR.

## 3. Ver logs correlacionados

```bash
# Local (docker-compose):
docker logs <container> | grep correlationId=<value>

# k8s:
kubectl logs -l app=risk-engine -n risk-engine | grep correlationId=<value>

# OpenObserve → Logs → buscar: correlationId="<value>"
```

## 4. Revisar metricas en el momento del incidente

```
# Prometheus:
http://localhost:9090

# Queries utiles:
histogram_quantile(0.99, rate(http_server_request_duration_seconds_bucket[5m]))
rate(risk_decisions_total{decision="DECLINE"}[5m])
risk_engine:request_success_rate:5m
```

## 5. Identificar causa raiz

Patrones comunes:

| Sintoma en trace | Causa probable |
|---|---|
| Span largo en `db.query` | Query lenta, falta indice, lock contention |
| Span largo en `ml.model.score` | ML service lento, circuit breaker deberia abrirse |
| Error en `outbox.relay.dispatch` | Redpanda no disponible |
| `TimeoutException` | SLA del servicio externo peor de lo esperado |
| Muchos retries en consumer | DLQ debe revisarse |

## 6. Fix y verificacion

1. Aplicar el fix.
2. Verificar con el skill `debug-failing-test` si hay test que cubra el caso.
3. Si no existe test: agregar uno que reproduzca el issue (TDD del bug).
4. Deploy y verificar que el correlationId similar ya no muestra el error.

## 7. Post-mortem breve

Guardar en Engram:
```
mem_save(title: "Fix: <descripcion>", type: "bugfix",
  content: "Root cause: <...>\nWhere: <archivo>\nFix: <...>\nLearned: <...>")
```
