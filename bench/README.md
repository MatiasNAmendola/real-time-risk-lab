# bench — Performance Benchmark Harness

Comparative performance testing for the four PoCs of this exploration.

## Modules

| Module | What it does |
|--------|-------------|
| `inprocess-bench` | JMH benchmark over `EvaluateRiskUseCase` — no HTTP, direct call |
| `distributed-bench` | HTTP load generator (virtual threads) against Vert.x controller-app |
| `runner` | Reads both result JSONs and generates a side-by-side comparison report |

## Quick start

```bash
# Build everything
cd bench && mvn package

# Run in-process only (no Docker required)
./scripts/run-inprocess.sh

# Run in-process fast (1 warmup, 3 iterations — ~2 min)
./scripts/run-inprocess.sh -wi 1 -i 3

# Run distributed (Vert.x must be up)
cd ../poc/java-vertx-distributed && docker compose up -d
cd ../../bench && ./scripts/run-distributed.sh

# Full comparison
./scripts/run-comparison.sh

# Skip distributed if Docker is not available
./scripts/run-comparison.sh --skip-distributed
```

## Output

```
out/
  perf/
    inprocess-jmh-<ts>.json   — JMH raw results (produced by run-inprocess.sh)
    <ts>/
      comparison.md            — Markdown table
      comparison.json          — Machine-readable
      comparison.txt           — Plain text summary
  inprocess/
    <ts>.txt                   — Full console output
    <ts>.json                  — JMH JSON results
  distributed/
    <ts>.json                  — Distributed run raw results
```

## How the comparison runner works

`runner/` reads two JSON files — one from `inprocess-bench` and one from `distributed-bench` — and merges them into a side-by-side comparison. The comparison runner normalises units (JMH uses ms/op; the distributed runner uses ms) and computes overhead ratios per percentile. Run it with:

```bash
./scripts/run-comparison.sh
# or explicitly:
java -jar runner/target/runner.jar \
  --inprocess out/perf/inprocess-jmh-<ts>.json \
  --distributed out/distributed/<ts>.json \
  --output out/perf/<ts>/
```

## Measured baseline — in-process JMH (2026-05-07)

Run: JMH 1.37, JDK 21.0.4 Temurin, Apple M1 Pro 16 GB, single thread, wi=1 i=3 f=1.

| Metric | Value | Note |
|--------|-------|------|
| p50 | 125 ns | Pure logic, no ML |
| p90 | ~208 ns | |
| p95 | ~250 ns | |
| p99 | 459 ns | |
| p99.9 | 26 µs | GC pause region |
| p99.99 | 386 µs | |
| p100 | 160 ms | FakeRiskModelScorer max sleep |
| Throughput | 25 ops/s | Single thread; JMH instrumentation overhead |
| Samples | 753 438 | Across 3 x 10s iterations |

**Throughput under concurrent load (BenchmarkRunner, 32 virtual threads, 5000 reqs):**

| Metric | Value |
|--------|-------|
| p50 | ~50 µs |
| p95 | ~127 ms |
| p99 | ~153 ms |
| Throughput | ~1528 req/s |

The gap between the two runners: JMH is single-threaded and measures pure method cost; BenchmarkRunner adds HTTP routing + 32-way concurrency + virtual thread scheduling overhead, which is why p50 jumps from 125ns to ~50µs.

## Distributed benchmark — PENDING

Distributed numbers are not yet measured. Run when Docker Desktop is available:

```bash
cd poc/java-vertx-distributed
docker compose up -d
# wait for all 5 pods to be healthy
docker compose ps
# then run the load generator
cd ../../bench
./scripts/run-distributed.sh
```

## Competition mode

Run both PoCs under identical HTTP workload and compare:

```bash
./scripts/competition.sh
./scripts/competition.sh --requests 10000 --concurrency 64
./scripts/competition.sh --quick
```

Output: `out/competition/<ts>/summary.md` + CSV + PNG (if matplotlib or gnuplot available).

Requires:
- Vert.x stack up: `cd poc/java-vertx-distributed && ./scripts/up.sh`.
- Bare-javac compiled: `cd poc/java-risk-engine && ./scripts/test.sh` (which compiles).
  The competition script will compile bare-javac automatically if needed.

```
out/competition/<ts>/
  summary.md          — Markdown side-by-side table with analysis
  summary.txt         — Plain text version
  comparison.json     — Machine-readable unified metrics
  comparison.csv      — metric, bare_javac_ms, vertx_distributed_ms, ratio
  bare.log            — stdout/stderr of the bare-javac HTTP server
  bare-results/       — per-run JSON from load gen against bare-javac
  vertx-results/      — per-run JSON from load gen against Vert.x
  latency-comparison.png  — bar chart (if plotter available)
```

## Variability disclaimer

Benchmark numbers vary 5-15% between runs due to:
- GC pauses (G1GC with -Xms256m -Xmx512m; ZGC would reduce tail latency)
- JIT compilation state (first fork has cold JIT; later forks benefit from profile data)
- Thermal throttling on Apple Silicon under sustained load
- OS scheduler decisions for virtual threads

For reliable comparison, always run with at least `-f 2 -wi 3 -i 5` and compare across the same host.
