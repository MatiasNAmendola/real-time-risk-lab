---
name: benchmark-poc
intent: Ejecutar un benchmark de latencia y throughput sobre una PoC y reportar p50/p95/p99
inputs: [poc_name, endpoint, concurrency, duration_seconds]
preconditions:
  - PoC corriendo (./scripts/run.sh exitoso)
  - k6, wrk, hey, o Go benchmark script disponible
postconditions:
  - Reporte con p50, p95, p99, max latency, RPS obtenidos
  - Comparativa contra SLO target (p99 < 300ms, 150 TPS)
  - Bottlenecks identificados si falla el SLO
related_rules: [observability-otel, java-version]
---

# Skill: benchmark-poc

## Opcion 1: k6

```javascript
// benchmark.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(99)<300'],
    http_reqs: ['rate>150'],
  },
};

export default function () {
  const res = http.post('http://localhost:8080/risk', JSON.stringify({
    transactionId: Math.random().toString(36),
    amountARS: Math.floor(Math.random() * 100000),
    merchantId: 'bench-merchant'
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}
```

```bash
k6 run benchmark.js
```

## Opcion 2: Go microbenchmark (en cli/risk-smoke o script local)

```bash
cd poc/java-risk-engine
./scripts/run.sh &
sleep 2
# hey -n 10000 -c 50 http://localhost:8080/risk -m POST -T application/json -d '...'
```

## Opcion 3: Java JMH (para benchmarks de unidad)

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class RuleEngineBenchmark {
    @Benchmark
    public RuleResult evaluateRules(BenchmarkState state) {
        return state.engine.evaluate(state.ctx);
    }
}
```

## Interpretar resultados

- p50 < 50ms: excelente para latencia de motor de reglas puro.
- p99 < 300ms: SLO objetivo del sistema.
- RPS > 150: requisito de throughput (150 TPS).
- Si p99 > 300ms: analizar con OTEL traces donde esta el tiempo (reglas, DB, ML).

## Notas
- Correr benchmark con JVM caliente (warm-up de al menos 30s antes de medir).
- Reportar en el README del PoC con fecha y hardware (MacBook M3 Pro, etc.).
