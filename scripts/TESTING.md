# Testing Guide

This document explains how to run all test suites in this monorepo and how to read the reports.

## Quick start

```bash
# Run everything that does not need running infrastructure (arch, cucumber, integration*, bench-inproc)
./scripts/test-all.sh --headless

# Run ALL suites — start docker compose automatically (java-vertx-distributed)
./scripts/test-all.sh --with-infra-compose --headless

# Run ALL suites — spin up k3d/OrbStack cluster with Helm addons + AWS mocks
./scripts/test-all.sh --with-infra-k8s --headless

# Override k8s provider explicitly
./scripts/test-all.sh --with-infra-k8s --provider orbstack
./scripts/test-all.sh --with-infra-k8s --provider k3d

# Teardown cluster after run (default: leave it running for faster re-runs)
./scripts/test-all.sh --with-infra-k8s --cleanup-k8s

# Run only specific suites
./scripts/test-all.sh --only arch,cucumber --headless

# Skip a specific suite
./scripts/test-all.sh --skip bench-inproc --headless

# Interactive (default) — pretty output
./scripts/test-all.sh
```

*`integration` only runs when Docker daemon is available; it is silently skipped otherwise.

## Infra modes

Three mutually exclusive modes control which suites are enabled and how infrastructure is managed.

| Mode | Flag | Suites available | Use when |
|---|---|---|---|
| No infra | _(default)_ | arch, cucumber, integration, bench-inproc | CI quick check; no services needed |
| Compose | `--with-infra-compose` | all of the above + smoke, karate, bench-dist | Iterating on app code; fastest full run |
| k8s | `--with-infra-k8s` | all of compose + k8s-smoke | Validating Helm deploy, Argo Rollouts, ESO, AWS mocks |

Passing both `--with-infra-compose` and `--with-infra-k8s` is an error.

### When to use each mode

**No infra** — use in CI pipelines or when you just changed business logic inside a single module. No Docker, no kubectl, no ports required.

**Compose (`--with-infra-compose`)** — use during active development on `poc/java-vertx-distributed`. Starts the full distributed Vert.x stack (via `./scripts/up.sh`) and shuts it down on exit. Fastest full-suite turnaround.

**k8s (`--with-infra-k8s`)** — use when you need to validate the real Kubernetes deployment path: Helm chart values, Argo Rollouts canary strategy, ServiceMonitors, External Secrets Operator, Redpanda, OpenObserve, AWS mocks (Moto/MinIO/OpenBao). Cluster setup takes longer, so by default the cluster is **left running** after the tests complete. Use `--cleanup-k8s` to tear it down.

## Suite reference

| ID | Name | Needs infra | Description |
|---|---|---|---|
| `arch` | ArchUnit | No | Static architecture checks (`tests/architecture/`) |
| `cucumber` | Cucumber bare | No | Cucumber-JVM ATDD against the bare-javac engine |
| `integration` | Testcontainers | Docker only | Testcontainers integration tests — spins its own ephemeral containers |
| `smoke` | Go smoke | Compose or k8s | Go smoke suite hitting `localhost:8080` (port-forwarded in k8s mode) |
| `karate` | Karate ATDD | Compose or k8s | Karate scenarios for the distributed Vert.x engine |
| `bench-inproc` | JMH in-process | No | JMH micro-benchmarks running inside the JVM |
| `bench-dist` | HTTP load gen | Compose or k8s | Distributed HTTP load benchmarks |
| `k8s-smoke` | k8s smoke (Ingress) | k8s only | Smoke tests via Traefik Ingress — no port-forward, hits port 80 directly |

## k8s bootstrap detail

When `--with-infra-k8s` is used, the orchestrator:

1. Checks that `kubectl` and `helm` are in PATH (and `k3d` or `orb` depending on provider). Missing tools → error with suggestion to run `./setup.sh --only kubernetes`.
2. Runs `poc/k8s-local/scripts/up.sh --provider <provider>`. Output goes to `infra.log`.
3. Waits for `risk-engine` pods Ready (180 s) and `aws-mocks/postgres` if the namespace exists (120 s).
4. Starts port-forwards in background:
   - `risk-engine` → `localhost:8080`
   - `redpanda` → `localhost:19092`
   - `openobserve` → `localhost:5080`
   - `moto`, `minio`, `openbao` if those services exist in the cluster
5. Health-checks `localhost:8080/healthz`.
6. Runs all suites (smoke/karate/bench-dist use `localhost`; k8s-smoke uses Ingress on port 80).
7. On exit: kills port-forward PIDs. Tears down cluster only if `--cleanup-k8s` was passed.

Provider auto-detection: checks for an `orbstack` kubectl context first, then for any `k3d-*` context. Falls back to `k3d`.

## Running a single suite manually

```bash
# ArchUnit
cd tests/architecture && ./gradlew test

# Cucumber ATDD (bare-javac engine)
./scripts/atdd-bare.sh

# Testcontainers integration (Docker must be running)
cd tests/integration && ./gradlew -Pintegration verify

# Go smoke checks (headless / CI)
cd cli/risk-smoke && ./bin/risk-smoke --headless

# Karate ATDD (needs infra)
./poc/java-vertx-distributed/scripts/atdd.sh

# JMH in-process benchmarks
./bench/scripts/run-inprocess.sh

# HTTP distributed benchmarks (needs infra)
./bench/scripts/run-distributed.sh
```

## Reading reports

Each `test-all.sh` run creates a timestamped output directory:

```
out/test-all/
├── latest -> 2026-05-07T18-30-00/     # symlink to most recent run
└── 2026-05-07T18-30-00/
    ├── summary.md                      # unified table of all suites
    ├── summary.txt                     # plain-text version
    ├── meta.json                       # run metadata (timestamp, mode, provider, git SHA, ...)
    ├── infra.log                       # full output of infra up/down (separate from suite logs)
    ├── arch/
    │   ├── stdout.log                  # full stdout of the suite
    │   ├── stderr.log                  # full stderr
    │   └── exit-code                   # numeric exit code
    ├── integration/
    │   └── ...
    └── ...
```

Quick read:

```bash
cat out/test-all/latest/summary.md
```

Individual suite detail:

```bash
cat out/test-all/latest/arch/stdout.log
cat out/test-all/latest/karate/stderr.log
```

Infrastructure log:

```bash
cat out/test-all/latest/infra.log
```

### meta.json fields

```json
{
  "timestamp": "2026-05-07T19:00:00Z",
  "mode": "infra-k8s",
  "provider": "orbstack",
  "host": "matias-mbp",
  "git_sha": "abc1234",
  "suites_run": ["arch", "cucumber", "smoke", "karate"],
  "suites_skipped": ["k8s-smoke"],
  "duration_total_seconds": 142
}
```

## Regenerating a report

```bash
./scripts/test-all-report.sh                           # regenerate latest
./scripts/test-all-report.sh out/test-all/2026-05-07T18-30-00
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Suite shows SKIP with "infra not up" | Run with `--with-infra-compose` or `--with-infra-k8s` |
| Suite shows SKIP with "requires --with-infra-k8s" | k8s-smoke needs the k8s mode specifically |
| `integration` shows SKIP with "Docker daemon not running" | Start Docker Desktop / OrbStack |
| `arch` fails with "Could not find project" | Check `tests/architecture/build.gradle.kts` exists |
| `kubectl not found` | Run `./setup.sh --only kubernetes` |
| `helm not found` | Run `./setup.sh --only kubernetes` |
| `k3d not found` | Run `./setup.sh --only kubernetes` |
| `ERROR: --with-infra-compose and --with-infra-k8s are mutually exclusive` | Choose one infra mode |
| risk-engine pods not Ready | Check `infra.log`; run `kubectl get pods -n risk` manually |
| Port-forward not binding | Another process may hold the port; kill it and re-run |
| Gradle timeout | Increase timeout in `test-all.sh` (default 600 s) or run the suite directly |

For any failed suite:

```bash
cat out/test-all/latest/<suite-id>/stderr.log
cat out/test-all/latest/<suite-id>/stdout.log
```
