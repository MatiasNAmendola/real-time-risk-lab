# Demo Script — Real-Time Risk Lab

Objetivo: mostrar criterio arquitectónico sin depender de una demo frágil.

## 0. Posicionamiento

Antes de correr comandos, abrir con la frase de [`vault/05-Methodology/Technical-Positioning.md`](vault/05-Methodology/Technical-Positioning.md):

> Te comparto una exploración técnica curada para discutir arquitectura de decisiones de riesgo en tiempo real. No intenta ser producción cerrada, sino una demo conversacional para hablar de trade-offs: Clean Architecture, boundaries, performance, trazabilidad, evaluación sincrónica, eventos asíncronos, permisos entre componentes, benchmarks y simulación local de despliegue distribuido.

## 1. Verificación previa

```bash
./nx setup --verify
./nx build
./nx test --composite quick
./nx audit consistency
./nx audit confidentiality
./nx scrub
```

Qué decir:

> “Antes de hablar de arquitectura, dejo visible el estado empírico: toolchain, build incremental, tests rápidos, boundaries, consistencia documental y scrub.”

Nota: `./gradlew clean build -x test` queda como verificación full/CI opcional. Para una demo live prefiero `./nx build` porque usa el orquestador del repo y evita reconstruir todo si los artefactos están frescos.

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
poc/vertx-layer-as-pod-http/scripts/run-local-pods.sh
poc/vertx-layer-as-pod-http/scripts/smoke.sh
```

Qué valida:

- controller/usecase/repository separados.
- scopes/tokens diferentes por capa.
- idempotent retry.
- acceso prohibido controller → repository con 403 esperado.

Cierre:

```bash
poc/vertx-layer-as-pod-http/scripts/stop-local-pods.sh
```

## 5. Benchmark principal

```bash
./nx bench inproc
```

Qué mostrar:

- JMH como medición reproducible.
- Diferencia entre latencia base del core y overhead distribuido.
- Que los números de performance se discuten con evidencia, no con intuición.

## 6. Deep dive opcional — compose distribuido

```bash
./nx up vertx
./nx demo rest --amount 200000
./nx down vertx
```

Usarlo solo si hay tiempo o si se quiere discutir migración runtime, healthchecks, EventBus/Hazelcast, OTEL e infra local. No es el demo principal: el demo principal es `risk-engine` + `vertx-layer-as-pod-http` local pods + smoke + benchmark.
