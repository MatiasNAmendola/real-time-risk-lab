# bench/k6 — Industry-standard load testing

[k6](https://k6.io) (Grafana Labs) replaces the custom `bench/scripts/competition.sh` runner.
Reasons: native ramp/spike/soak stages, JS-scriptable scenarios, Prometheus
remote-write, real distributions (p95/p99/p999 from HDR-style histograms),
and a single-binary install. See [ADR-0040](../../vault/02-Decisions/0040-k6-for-load-testing.md).

## Layout

```
bench/k6/
├── lib/
│   ├── config.js       # base URLs per service (env-driven)
│   ├── payload.js      # RiskRequest generator
│   └── thresholds.js   # SLO assertions (p99 < 300ms)
├── scenarios/
│   ├── smoke.js        # 1 VU, 30s   — service is alive
│   ├── load.js         # 32 VUs, 2m  — sustained, gates SLO
│   ├── stress.js       # ramp 0→100, 5m — find the knee
│   ├── spike.js        # 0→200 in 30s, hold 1m, ramp down — burst tolerance
│   └── soak.js         # 16 VUs, 30m — leak detection
└── profiles/           # per-service metadata (port, expected SLO)
```

## Targets

`./nx bench k6 <scenario> --target <no-vertx-clean-engine|vertx-monolith-inprocess|vertx-layer-as-pod-http|vertx-layer-as-pod-eventbus>`

| Target | Default URL | Profile |
|---|---|---|
| `no-vertx-clean-engine`            | `http://localhost:8081` | `profiles/no-vertx-clean-engine.json`      |
| `vertx-monolith-inprocess`        | `http://localhost:8090` | `profiles/vertx-monolith-inprocess.json`        |
| `vertx-layer-as-pod-http`  | `http://localhost:8180` | `profiles/vertx-layer-as-pod-http.json`  |
| `vertx-layer-as-pod-eventbus`     | `http://localhost:8080` | `profiles/vertx-layer-as-pod-eventbus.json`     |

Override via env: `NO_VERTX_CLEAN_ENGINE_URL`, `VERTX_MONOLITH_INPROCESS_URL`, `VERTX_LAYER_AS_POD_HTTP_URL`, `VERTX_LAYER_AS_POD_EVENTBUS_URL`, or `BASE_URL` (wins over `TARGET`).

## Run individually (no nx wrapper)

```bash
# Smoke against no-vertx-clean-engine
TARGET=no-vertx-clean-engine k6 run bench/k6/scenarios/smoke.js

# Load with overrides
TARGET=vertx-layer-as-pod-eventbus k6 run -e VUS=64 -e DURATION=3m bench/k6/scenarios/load.js

# Stress
TARGET=vertx-layer-as-pod-http k6 run bench/k6/scenarios/stress.js
```

Output to JSON for post-analysis:

```bash
k6 run --out json=out/k6/$(date +%Y%m%dT%H%M%S)/load.json \
  bench/k6/scenarios/load.js
```

## Run via ./nx (recommended)

```bash
./nx bench k6 smoke                                # default --target vertx-layer-as-pod-eventbus
./nx bench k6 load --target no-vertx-clean-engine --duration 60s
./nx bench k6 stress --target vertx-layer-as-pod-http
./nx bench k6 spike --target vertx-layer-as-pod-eventbus
./nx bench k6 soak --target vertx-layer-as-pod-eventbus --vus 8 --duration 5m

# 4-way comparison: same scenario against all four services
./nx bench k6 competition load
```

The wrapper writes results to `out/k6/<scenario>/<timestamp>/`
and `out/k6-competition/<timestamp>/` for `competition`.

## Push metrics to OpenObserve (Prometheus remote-write)

OpenObserve speaks Prometheus remote-write. k6 has a native experimental output
for it. Set the env vars and add `-o experimental-prometheus-rw`:

```bash
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:5080/api/prom/push
export K6_PROMETHEUS_RW_USERNAME=admin@example.com
export K6_PROMETHEUS_RW_PASSWORD='${OPENOBSERVE_PASSWORD:-change-me-openobserve-local}'
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),min,max,avg'

k6 run -o experimental-prometheus-rw bench/k6/scenarios/load.js
```

Once flowing, query in OpenObserve:

- Stream: `default` (metrics)
- Metric prefix: `k6_*` (e.g. `k6_http_req_duration`, `k6_http_reqs`,
  `k6_http_req_failed`)
- Group by `scenario`, `target`, `method`, `url`

The wrapper auto-enables this output if `K6_PROMETHEUS_RW_SERVER_URL` is set.

## SLO thresholds

`lib/thresholds.js` exports three presets:

| Preset       | p99       | error rate | Use for       |
|---           |---        |---         |---            |
| `sloStrict`  | < 300ms   | < 1%       | load          |
| `sloRelaxed` | < 3000ms  | < 5%       | smoke/local cold-start gate |
| `sloSoak`    | < 400ms   | < 2%       | soak          |

`stress` and `spike` intentionally don't gate on SLO — they push past it.

## Example: smoke run output

```
$ TARGET=no-vertx-clean-engine k6 run bench/k6/scenarios/smoke.js
   ✓ status is 2xx
   ✓ has decision

   checks.........................: 100.00% ✓ 274  ✗ 0
   http_req_duration..............: avg=4.2ms  med=3.1ms  p(95)=11.4ms p(99)=18.7ms
   http_req_failed................: 0.00%   ✓ 0    ✗ 274
   http_reqs......................: 274     9.13/s
   iteration_duration.............: avg=109.5ms
```

## Why k6 over the custom Java bench

The custom `bench/distributed-bench` does HTTP load via a hand-rolled
thread pool. k6 gives:

1. Real ramp/spike/soak stages (custom bench only had fixed concurrency).
2. HDR-style latency histograms (custom bench computed avg/p95 from a
   bounded array — biased on long-tail).
3. Native Prometheus remote-write — no glue code.
4. `--out json` for post-mortem; `--out csv` for spreadsheets.
5. Threshold gating (`http_req_duration: ['p(99)<300']`) baked in;
   exit code 99 on failure makes it CI-friendly.
6. A single static binary; no Gradle build, no JVM warmup.

The Java bench is kept for inproc JMH (no HTTP, no network — that's
where it shines). HTTP-level competition lives in k6.
