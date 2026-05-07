# Demo Script — Risk Decision Platform

Objetivo: mostrar criterio arquitectónico sin depender de una demo frágil.

## 0. Posicionamiento

Antes de correr comandos, abrir con la frase de [`docs/36-technical-interview-positioning.md`](docs/36-technical-interview-positioning.md):

> Armé una plataforma de práctica para explorar decisiones de riesgo en tiempo real. No intenta ser producción cerrada, sino una demo técnica para discutir arquitectura: camino crítico sincrónico, eventos asíncronos, trazabilidad, separación por bounded contexts, permisos entre componentes, benchmarking y trade-offs de operación.

## 1. Verificación previa

```bash
./nx setup --verify
./nx test --composite quick
./nx audit consistency
./nx audit confidentiality
./nx scrub
```

Qué decir:

> “Antes de hablar de arquitectura, dejo visible el estado empírico: toolchain, tests rápidos, boundaries, consistencia documental y scrub.”

## 2. Explicar el caso

- 150 TPS sostenidos.
- p99 < 300ms.
- Decisión sync: approve/reject/review.
- Async: auditoría, eventos, downstream, ML feedback.
- Separación entre camino crítico y trabajo fuera del budget.

Frase útil:

> “Primero defino qué entra en los 300ms y qué saco del critical path.”

## 3. Demo HTTP mínima

```bash
./nx run risk-engine
```

En otra terminal:

```bash
curl -s -X POST http://localhost:8080/risk \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-demo","customerId":"cust-1","amountCents":200000,"correlationId":"demo-1","idempotencyKey":"demo-1","newDevice":true}' | jq .
```

Qué mostrar:

- Decision response.
- correlationId.
- input determinístico.
- boundary limpio entre input adapter y use case.

## 4. Demo local pods/permisos

```bash
poc/vertx-risk-platform/scripts/run-local-pods.sh
poc/vertx-risk-platform/scripts/smoke.sh
```

Qué valida:

- controller/usecase/repository separados.
- scopes/tokens diferentes por capa.
- idempotent retry.
- acceso prohibido controller → repository con 403 esperado.

Cierre:

```bash
poc/vertx-risk-platform/scripts/stop-local-pods.sh
```

## 5. Deep dive opcional

```bash
./nx up vertx
./nx demo rest --amount 200000
./nx down vertx
```

Usarlo solo si hay tiempo o si se quiere discutir migración runtime, healthchecks, EventBus/Hazelcast, OTEL e infra local.
