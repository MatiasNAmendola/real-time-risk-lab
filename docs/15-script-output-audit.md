# Script Output Audit

All scripts that can be run by developers/CI. The convention for every run is:
- Output dir: `out/<name>/<ISO-timestamp>/`
- Symlink: `out/<name>/latest -> <last-ts>`
- Every run keeps at minimum: `stdout.log`, `stderr.log`, `meta.json`
- Scripts that produce summary content also write `summary.md` and `summary.txt`
- All scripts print `Output: out/<name>/<ts>/` at the end

The shared helper lives at `scripts/lib/output.sh` (`init_output <name>` + `finalize_output <exit>`).

---

| Script | Output dir | Timestamped | latest symlink | Failure log | Status |
|---|---|---|---|---|---|
| `cd cli/risk-smoke && go run .` | `out/smoke/<ts>/` | yes | yes | yes | OK (unchanged) |
| `cli/risk-smoke/scripts/demo.sh` | `out/risk-smoke-demo/<ts>/` | yes | yes | yes | FIXED |
| `scripts/test-all.sh` | `out/test-all/<ts>/` | yes | yes | yes | OK (unchanged) |
| `scripts/test-all-report.sh` | regenerates inside existing run dir | n/a | n/a | n/a | OK (no own output dir needed) |
| `scripts/atdd-bare.sh` | `out/atdd-cucumber/<ts>/` (via `tests/risk-engine-atdd/scripts/report.sh`) | yes | yes | yes | OK (unchanged) |
| `scripts/atdd-bare-coverage.sh` | `out/atdd-cucumber/<ts>/` (delegates to report.sh) | yes | yes | yes | OK (unchanged) |
| `scripts/integration-tests.sh` | `out/integration-tests/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-vertx-distributed/scripts/atdd.sh` | `out/atdd-karate/<ts>/` (via atdd-report.sh) | yes | yes | yes | OK (unchanged) |
| `poc/java-vertx-distributed/scripts/atdd-coverage.sh` | delegates to atdd.sh + jacoco | yes | yes | yes | OK (unchanged) |
| `poc/java-vertx-distributed/scripts/atdd-report.sh` | `out/atdd-karate/<ts>/` | yes | yes | yes | OK (unchanged) |
| `poc/java-vertx-distributed/scripts/build.sh` | `out/vertx-build/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-vertx-distributed/scripts/up.sh` | `out/vertx-up/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-vertx-distributed/scripts/down.sh` | `out/vertx-down/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-vertx-distributed/scripts/demo.sh` | `out/vertx-demo/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-vertx-distributed/scripts/fetch-jacoco-agent.sh` | no output dir (idempotent downloader, no run state) | n/a | n/a | n/a | OK (TODO: add if needed) |
| `poc/java-risk-engine/scripts/run.sh` | `out/risk-engine-run/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-risk-engine/scripts/test.sh` | `out/risk-engine-test/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-risk-engine/scripts/benchmark.sh` | `out/risk-engine-benchmark/<ts>/` | yes | yes | yes | FIXED |
| `poc/java-risk-engine/scripts/run-http.sh` | `out/risk-engine-http/<ts>/` | yes | yes | yes | FIXED |
| `poc/k8s-local/scripts/up.sh` | `out/k8s-up/<ts>/` + `cluster-state.txt` | yes | yes | yes | FIXED |
| `poc/k8s-local/scripts/down.sh` | `out/k8s-down/<ts>/` | yes | yes | yes | FIXED |
| `poc/k8s-local/scripts/status.sh` | `out/k8s-status/<ts>/` + `cluster-state.txt` | yes | yes | yes | FIXED |
| `poc/k8s-local/scripts/demo.sh` | `out/k8s-demo/<ts>/` | yes | yes | yes | FIXED |
| `poc/vertx-risk-platform/scripts/run-local-pods.sh` | stdout only (./gradlew wrapper call) | no | no | no | TODO: add output.sh if this PoC is used actively |
| `poc/vertx-risk-platform/scripts/run-local-pods.sh` | passthrough to ./gradlew — not a run script | n/a | n/a | n/a | OK (not a standalone run) |
| `poc/vertx-risk-platform/scripts/run-local-pods.sh` | `out/vertx-platform-run/<ts>/` + per-pod `.log` | yes | yes | yes | FIXED |
| `poc/vertx-risk-platform/scripts/smoke.sh` | `out/vertx-platform-smoke/<ts>/` | yes | yes | yes | FIXED |
| `poc/vertx-risk-platform/scripts/stop-local-pods.sh` | `out/vertx-platform-stop/<ts>/` | yes | yes | yes | FIXED |
| `bench/scripts/run-inprocess.sh` | `out/bench-inprocess/<ts>/` + `results.json` + `summary.md` | yes | yes | yes | FIXED (was PARTIAL) |
| `bench/scripts/run-distributed.sh` | `out/bench-distributed/<ts>/` | yes | yes | yes | FIXED |
| `bench/scripts/run-comparison.sh` | `out/bench-comparison/<ts>/` | yes | yes | yes | FIXED |
| `setup.sh` (root) | no output dir (interactive installer, installs system tools) | n/a | n/a | n/a | OK (setup scripts are not run workflows) |
| `scripts/setup/setup.sh` | same as above | n/a | n/a | n/a | OK |
| `scripts/setup/verify.sh` | stdout only — verify is read-only, no side effects | n/a | n/a | n/a | OK |
| `tests/risk-engine-atdd/scripts/report.sh` | called by `atdd-bare.sh`; writes to `out/atdd-cucumber/<ts>/` | yes | yes | yes | OK (unchanged) |

---

## Helper library

`scripts/lib/output.sh` — source in any script:

```bash
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/<rel-to-repo-root>" && pwd)"
source "$REPO_ROOT/scripts/lib/output.sh"
init_output "my-name"          # sets OUT_DIR, OUT_BASE, OUT_TS; creates dir + latest symlink
# ... do work, write to $OUT_DIR/stdout.log / stderr.log ...
finalize_output "$exit_code"   # writes meta.json; prints "Output: ..." line
```

## TODOs left

- `poc/vertx-risk-platform/scripts/run-local-pods.sh` — passes through to `gradle-local.sh`; if this PoC becomes active add `output.sh` integration.
- `poc/java-vertx-distributed/scripts/fetch-jacoco-agent.sh` — single-purpose idempotent downloader; a run log would be noise. Add only if there's a CI need to audit agent downloads.
- Existing scripts that already produced output (`cd cli/risk-smoke && go run .`, `scripts/test-all.sh`, `scripts/atdd-bare.sh`, `poc/java-vertx-distributed/scripts/atdd.sh`, `tests/risk-engine-atdd/scripts/report.sh`) were not refactored to use `output.sh` to avoid regressions — their existing logic is equivalent. Migrate opportunistically.
