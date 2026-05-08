# Testing Guide

> **Canonical source:** the active test taxonomy lives in `.ai/test-groups.yaml`
> and is documented in `docs/27-test-runner.md`. This file is a compatibility
> guide for older `scripts/test-all.sh` workflows; do not add new suite taxonomy
> here.

## Recommended commands

| Context | Command | Notes |
|---|---|---|
| Live demo guardrail | `./nx test --composite quick` | Sub-second/seconds check; no Gradle/JUnit. |
| Pre-push / real fast verification | `./nx test --composite ci-fast` | Unit Java + SDK units + ArchUnit, with `arch` isolated. |
| Full local non-k8s sweep | `./nx test all --with-infra-compose` | Uses Docker/Compose where required; conservative local parallelism. |
| Kubernetes / infra validation | `./nx test --composite k8s --auto-infra` | Requires k8s tooling. |
| List groups and composites | `./nx test --list` | Reads `.ai/test-groups.yaml`. |

For the full taxonomy, resource model, skip-on-missing-tool behavior, and output
format, read `docs/27-test-runner.md`.

## Current suite taxonomy summary

This summary mirrors `docs/27-test-runner.md`; if it drifts, update that doc and
`.ai/test-groups.yaml` first.

| Slice | Canonical group/composite | Infra | Purpose |
|---|---|---|---|
| Guardrail live | `quick-check` / `quick` | No | Source boundaries + artifact freshness warnings without Gradle. |
| Unit Java fast | `unit-java-fast` | No | Explicit Gradle unit slice; never `./gradlew test`. |
| Unit SDK | `unit-sdk` | No | Java/TypeScript/Go SDK unit tests. |
| Architecture | `arch` | No | ArchUnit; exclusive to avoid Gradle/JUnit XML report races. |
| Component Vert.x | `component-vertx-layer-as-pod-http` | No | In-process Vert.x component tests. |
| Integration Testcontainers | `integration-testcontainers` | Docker | Ephemeral container integration tests. |
| Integration Compose | `integration-compose` | Compose | ATDD/smoke suites against local distributed stack. |
| SDK integration | `sdk-integration` | Compose | SDKs against a real local server. |
| Contract | `contract` | Compose | Cross-SDK/API contract verification. |
| Load smoke | `k6` | Compose + k6 | k6 smoke/load guardrail. |
| Kubernetes | `k8s` / `ci-k8s` | k8s | Deployment, rollout, ArgoCD and ESO validation. |

## Output locations

`nx test` writes the canonical report format:

```text
out/test-runner/<timestamp>/
  plan.json
  summary.md
  results.json
  job-<name>.log
out/test-runner/latest -> <timestamp>/
```

Quick read:

```bash
cat out/test-runner/latest/summary.md
```

## Legacy `scripts/test-all.sh`

`scripts/test-all.sh` is kept for compatibility with older docs/runs. Prefer
`./nx test ...` for new work.

If you must use the legacy runner:

```bash
./scripts/test-all.sh --headless
./scripts/test-all.sh --with-infra-compose --headless
./scripts/test-all.sh --with-infra-k8s --headless
./scripts/test-all.sh --only arch,cucumber --headless
```

Legacy output goes to `out/test-all/<timestamp>/`. Do not use its suite list as
the source of truth; verify current group names with `./nx test --list`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `quick` passes but you need real JUnit/ArchUnit | Run `./nx test --composite ci-fast`. |
| Compose-dependent suite is skipped/fails because infra is absent | Run with `--auto-infra` or start the stack explicitly. |
| Docker/Testcontainers suite fails before tests start | Start Docker Desktop/OrbStack and rerun the specific group. |
| k8s suite cannot find `kubectl`, `helm`, or `k3d` | Run `./nx setup --verify` and install missing optional tooling. |
| ArchUnit XML/reporting race appears | Ensure `arch` remains `exclusive: true` and runs in its own scheduler level. |
| Laptop freezes during `all` | Do not use `--auto-parallel` locally. The runner defaults to `--parallel 2` and keeps Gradle single-flight; if needed, lower further with `./nx test all --with-infra-compose --parallel 1 --max-cpu 50 --max-ram 6000`. |
| Need to inspect or stop stuck `nx`/Gradle test processes | Use `./nx proc status` and `./nx proc stop` instead of ad-hoc `ps | grep`. `stop` is dry-run unless `--yes` is passed. |
| Need to add a suite | Edit `.ai/test-groups.yaml`, then update `docs/27-test-runner.md`. |

For failed `nx test` jobs:

```bash
cat out/test-runner/latest/job-<group>.log
cat out/test-runner/latest/results.json
```
