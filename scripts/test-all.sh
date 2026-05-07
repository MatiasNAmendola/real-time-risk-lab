#!/usr/bin/env bash
# test-all.sh — Unified test orchestrator for the naranjax/practica-entrevista monorepo.
# Runs all test suites and produces a unified report under out/test-all/<timestamp>/.
#
# Usage:
#   ./scripts/test-all.sh                              # run everything that does NOT need infra
#   ./scripts/test-all.sh --with-infra-compose         # docker compose up vertx, then run all
#   ./scripts/test-all.sh --with-infra-k8s             # k3d/OrbStack + Helm + addons deployed
#   ./scripts/test-all.sh --with-infra-k8s --provider orbstack
#   ./scripts/test-all.sh --with-infra-k8s --provider k3d
#   ./scripts/test-all.sh --with-infra-k8s --cleanup-k8s   # teardown cluster after run
#   ./scripts/test-all.sh --only smoke,karate
#   ./scripts/test-all.sh --skip kafka
#   ./scripts/test-all.sh --headless         # plain text output (no TUI/ANSI tricks)
#   ./scripts/test-all.sh --help

# ---------------------------------------------------------------------------
# Auto-re-exec with brew bash on macOS to get bash >= 4 features
# ---------------------------------------------------------------------------
if [[ "${BASH_VERSINFO[0]}" -lt 4 ]]; then
  BREW_BASH=""
  if command -v brew >/dev/null 2>&1; then
    BREW_BASH="$(brew --prefix 2>/dev/null)/bin/bash"
  fi
  if [[ -n "$BREW_BASH" && -x "$BREW_BASH" ]]; then
    exec "$BREW_BASH" "$0" "$@"
  fi
  # Fall through — bash 3.2 compatible code follows (we avoid associative arrays)
fi

set -euo pipefail

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

OUT_BASE="$PROJECT_ROOT/out/test-all"

# Suite definitions — order matters
# Format: "id"
# needs_infra values: 0 = none, 1 = any infra, 2 = k8s-only
SUITE_IDS=(arch cucumber integration smoke karate bench-inproc bench-dist k8s-smoke coverage-audit)

suite_needs_infra_arch=0
suite_needs_infra_cucumber=0
suite_needs_infra_integration=0
suite_needs_infra_smoke=1
suite_needs_infra_karate=1
suite_needs_infra_bench_inproc=0
suite_needs_infra_bench_dist=1
suite_needs_infra_k8s_smoke=2
suite_needs_infra_coverage_audit=0

suite_label_arch="ArchUnit"
suite_label_cucumber="Cucumber bare"
suite_label_integration="Testcontainers"
suite_label_smoke="Go smoke"
suite_label_karate="Karate ATDD"
suite_label_bench_inproc="JMH in-process"
suite_label_bench_dist="HTTP load gen"
suite_label_k8s_smoke="k8s smoke (Ingress)"
suite_label_coverage_audit="Coverage audit"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
ts_iso() { date -u "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u "+%Y-%m-%dT%H:%M:%SZ"; }
ts_slug() { date -u "+%Y-%m-%dT%H-%M-%S"; }
now_ns() {
  # Portable nanosecond timestamp; falls back to seconds on systems without %N
  if date +%s%N 2>/dev/null | grep -qv 'N'; then
    date +%s%N
  else
    echo "$(($(date +%s) * 1000000000))"
  fi
}

# Translate suite id to a shell-safe variable name prefix (hyphens -> underscores)
suite_var() { echo "${1//-/_}"; }

suite_needs_infra() {
  local var="suite_needs_infra_$(suite_var "$1")"
  echo "${!var:-0}"
}

suite_label() {
  local var="suite_label_$(suite_var "$1")"
  echo "${!var:-$1}"
}

log()  { echo "$@"; }
info() { log "  $*"; }

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
WITH_INFRA_COMPOSE=0
WITH_INFRA_K8S=0
K8S_PROVIDER=""
CLEANUP_K8S=0
HEADLESS=0
ONLY_LIST=""
SKIP_LIST=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-infra-compose) WITH_INFRA_COMPOSE=1; shift ;;
    --with-infra-k8s)     WITH_INFRA_K8S=1; shift ;;
    --provider)           K8S_PROVIDER="$2"; shift 2 ;;
    --provider=*)         K8S_PROVIDER="${1#--provider=}"; shift ;;
    --cleanup-k8s)        CLEANUP_K8S=1; shift ;;
    --headless)           HEADLESS=1; shift ;;
    --only)               ONLY_LIST="$2"; shift 2 ;;
    --only=*)             ONLY_LIST="${1#--only=}"; shift ;;
    --skip)               SKIP_LIST="$2"; shift 2 ;;
    --skip=*)             SKIP_LIST="${1#--skip=}"; shift ;;
    --help|-h)
      cat <<'EOF'
test-all.sh — Unified test orchestrator

USAGE
  ./scripts/test-all.sh [flags]

FLAGS
  --with-infra-compose         docker compose up (java-vertx-distributed), then run all infra-dependent suites
  --with-infra-k8s             spin up k3d/OrbStack cluster with Helm addons + AWS mocks, then run all suites
  --provider <orbstack|k3d>    override k8s provider (auto-detected when not set)
  --cleanup-k8s                teardown the k8s cluster after the run (default: leave it running)
  --only <ids>                 comma-separated list of suite IDs to run
  --skip <ids>                 comma-separated list of suite IDs to skip
  --headless                   plain text output (no ANSI formatting)
  --help                       show this help and exit

  NOTE: --with-infra-compose and --with-infra-k8s are mutually exclusive.

SUITE IDs
  arch          ArchUnit static architecture checks (no infra)
  cucumber      Cucumber-JVM ATDD for bare-javac engine (no infra)
  integration   Testcontainers integration tests — needs Docker running (no compose/k8s)
  smoke         Go smoke checks for java-vertx-distributed (needs infra)
  karate        Karate ATDD integration tests (needs infra)
  bench-inproc  JMH in-process benchmarks (no infra)
  bench-dist    HTTP distributed load benchmarks (needs infra)
  k8s-smoke     Smoke tests via Ingress/Traefik (needs --with-infra-k8s only)

EXAMPLES
  ./scripts/test-all.sh                                  # run everything possible without infra
  ./scripts/test-all.sh --with-infra-compose             # run ALL suites (starts docker compose)
  ./scripts/test-all.sh --with-infra-k8s                 # run ALL suites against real k8s cluster
  ./scripts/test-all.sh --with-infra-k8s --provider k3d  # force k3d provider
  ./scripts/test-all.sh --with-infra-k8s --cleanup-k8s   # teardown cluster after run
  ./scripts/test-all.sh --only arch,cucumber
  ./scripts/test-all.sh --skip bench-inproc
  ./scripts/test-all.sh --headless                       # CI-friendly output

REPORTS
  out/test-all/latest/summary.md
  out/test-all/latest/meta.json
  out/test-all/latest/infra.log
  out/test-all/latest/<suite-id>/stdout.log
  out/test-all/latest/<suite-id>/stderr.log
  out/test-all/latest/<suite-id>/exit-code
EOF
      exit 0
      ;;
    *)
      echo "Unknown flag: $1" >&2
      echo "Run with --help for usage." >&2
      exit 1
      ;;
  esac
done

# Mutual exclusion guard
if [[ $WITH_INFRA_COMPOSE -eq 1 && $WITH_INFRA_K8S -eq 1 ]]; then
  echo "ERROR: --with-infra-compose and --with-infra-k8s are mutually exclusive." >&2
  echo "       Choose one infra mode and re-run." >&2
  exit 1
fi

# Derive unified infra mode string
if [[ $WITH_INFRA_COMPOSE -eq 1 ]]; then
  INFRA_MODE="infra-compose"
elif [[ $WITH_INFRA_K8S -eq 1 ]]; then
  INFRA_MODE="infra-k8s"
else
  INFRA_MODE="no-infra"
fi

# ---------------------------------------------------------------------------
# Filter suite list based on --only / --skip
# ---------------------------------------------------------------------------
enabled_suites=()
for id in "${SUITE_IDS[@]}"; do
  # --only filter
  if [[ -n "$ONLY_LIST" ]]; then
    found=0
    IFS=',' read -ra only_arr <<< "$ONLY_LIST"
    for o in "${only_arr[@]}"; do
      [[ "$(echo "$o" | tr -d ' ')" == "$id" ]] && found=1 && break
    done
    [[ $found -eq 0 ]] && continue
  fi
  # --skip filter
  if [[ -n "$SKIP_LIST" ]]; then
    skip=0
    IFS=',' read -ra skip_arr <<< "$SKIP_LIST"
    for s in "${skip_arr[@]}"; do
      [[ "$(echo "$s" | tr -d ' ')" == "$id" ]] && skip=1 && break
    done
    [[ $skip -eq 1 ]] && continue
  fi
  enabled_suites+=("$id")
done

if [[ ${#enabled_suites[@]} -eq 0 ]]; then
  echo "No suites to run (check --only / --skip values)." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Output directory (idempotent: if exists, add suffix)
# ---------------------------------------------------------------------------
RUN_SLUG="$(ts_slug)"
OUT_DIR="$OUT_BASE/$RUN_SLUG"
suffix=1
while [[ -d "$OUT_DIR" ]]; do
  OUT_DIR="$OUT_BASE/${RUN_SLUG}-${suffix}"
  suffix=$((suffix + 1))
done
mkdir -p "$OUT_DIR"

INFRA_LOG="$OUT_DIR/infra.log"
touch "$INFRA_LOG"

# ---------------------------------------------------------------------------
# k8s helper: detect provider when not explicitly set
# ---------------------------------------------------------------------------
k8s_detect_provider() {
  if [[ -n "$K8S_PROVIDER" ]]; then
    echo "$K8S_PROVIDER"
    return
  fi
  # OrbStack registers itself as a kubectl context named "orbstack"
  if kubectl config get-contexts orbstack >/dev/null 2>&1; then
    echo "orbstack"
    return
  fi
  # k3d registers contexts as k3d-<cluster>
  if kubectl config get-contexts 2>/dev/null | grep -q 'k3d-'; then
    echo "k3d"
    return
  fi
  # fallback
  echo "k3d"
}

# ---------------------------------------------------------------------------
# Infrastructure management
# ---------------------------------------------------------------------------
INFRA_UP=0
INFRA_CLEANUP_NEEDED=0

# PIDs of background port-forwards (k8s mode only)
PF_PIDS=()

infra_healthcheck() {
  curl -sf --max-time 2 "http://localhost:8080/healthz" >/dev/null 2>&1
}

# --- compose bootstrap ------------------------------------------------------
bootstrap_compose() {
  log ""
  log "Bootstrapping compose..."
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker is not installed or not in PATH." >&2
    exit 1
  fi
  info "starting java-vertx-distributed stack"
  (
    cd "$PROJECT_ROOT/poc/java-vertx-distributed"
    bash scripts/up.sh
  ) >> "$INFRA_LOG" 2>&1
  INFRA_UP=1
  INFRA_CLEANUP_NEEDED=1
  # Wait for healthz
  local i
  for i in $(seq 1 20); do
    if infra_healthcheck; then
      info "stack healthy"
      break
    fi
    sleep 2
    if [[ $i -eq 20 ]]; then
      echo "WARNING: infra did not become healthy in time." >&2
    fi
  done
}

# --- k8s bootstrap ----------------------------------------------------------
bootstrap_k8s() {
  local provider
  provider="$(k8s_detect_provider)"
  K8S_PROVIDER="$provider"   # store for meta.json

  log ""
  log "Bootstrapping k8s..."

  # 1. Prerequisites check
  if ! command -v kubectl >/dev/null 2>&1; then
    echo "ERROR: kubectl not found in PATH." >&2
    echo "       Run './setup.sh --only kubernetes' to install prerequisites." >&2
    exit 1
  fi
  if ! command -v helm >/dev/null 2>&1; then
    echo "ERROR: helm not found in PATH." >&2
    echo "       Run './setup.sh --only kubernetes' to install prerequisites." >&2
    exit 1
  fi
  if [[ "$provider" == "k3d" ]] && ! command -v k3d >/dev/null 2>&1; then
    echo "ERROR: k3d not found in PATH (provider=k3d)." >&2
    echo "       Run './setup.sh --only kubernetes' to install prerequisites." >&2
    exit 1
  fi
  if [[ "$provider" == "orbstack" ]] && ! command -v orb >/dev/null 2>&1; then
    echo "WARNING: orb CLI not found; OrbStack must already be running." >&2
  fi
  info "using provider: $provider"

  # 2. Spin up the cluster + addons
  info "running poc/k8s-local/scripts/up.sh --provider $provider"
  (
    cd "$PROJECT_ROOT/poc/k8s-local"
    bash scripts/up.sh --provider "$provider"
  ) >> "$INFRA_LOG" 2>&1
  info "cluster ready"

  # 3. Wait for key pods
  info "waiting for risk-engine pods (timeout 180s)..."
  kubectl wait --for=condition=Ready pod \
    -l app=risk-engine -n risk \
    --timeout=180s >> "$INFRA_LOG" 2>&1 || {
      echo "WARNING: risk-engine pods did not become Ready in time." >&2
    }

  # postgres in aws-mocks namespace is optional — best-effort
  if kubectl get ns aws-mocks >/dev/null 2>&1; then
    info "waiting for postgres pod in aws-mocks (timeout 120s)..."
    kubectl wait --for=condition=Ready pod \
      -l app=postgres -n aws-mocks \
      --timeout=120s >> "$INFRA_LOG" 2>&1 || {
        echo "WARNING: aws-mocks/postgres pod did not become Ready in time." >&2
      }
  fi

  # 4. Port-forwards in background
  info "starting port-forwards..."
  _start_pf() {
    local ns="$1" svc="$2" local_port="$3" remote_port="$4"
    kubectl -n "$ns" port-forward "svc/$svc" "${local_port}:${remote_port}" \
      >> "$INFRA_LOG" 2>&1 &
    PF_PIDS+=($!)
  }
  _start_pf risk        risk-engine   8080 8080
  _start_pf redpanda    redpanda      19092 9092
  _start_pf openobserve openobserve   5080 5080

  # Moto / MinIO / OpenBao — start if services exist in cluster
  if kubectl get svc -n aws-mocks moto >/dev/null 2>&1; then
    _start_pf aws-mocks moto 5000 5000
  fi
  if kubectl get svc -n aws-mocks minio >/dev/null 2>&1; then
    _start_pf aws-mocks minio 9000 9000
  fi
  if kubectl get svc -n openbao openbao >/dev/null 2>&1; then
    _start_pf openbao openbao 8200 8200
  fi

  # Give port-forwards a moment to bind
  sleep 2
  info "port-forwards started (${#PF_PIDS[@]} processes)"

  # 5. Health check post-port-forward
  local i
  for i in $(seq 1 15); do
    if infra_healthcheck; then
      info "risk-engine healthy at localhost:8080"
      break
    fi
    sleep 2
    if [[ $i -eq 15 ]]; then
      echo "WARNING: risk-engine did not respond at localhost:8080 in time." >&2
    fi
  done

  INFRA_UP=1
  INFRA_CLEANUP_NEEDED=1
}

# Determine k8s context name to display (best-effort)
k8s_current_context() {
  kubectl config current-context 2>/dev/null || echo "unknown"
}

# Check if Docker daemon is available (for Testcontainers)
docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

# Bootstrap dispatch
if [[ $WITH_INFRA_COMPOSE -eq 1 ]]; then
  bootstrap_compose
elif [[ $WITH_INFRA_K8S -eq 1 ]]; then
  bootstrap_k8s
  log "  [k8s] using context: $(k8s_current_context)"
else
  # No explicit infra requested — detect whether something is already up
  if infra_healthcheck; then
    INFRA_UP=1
    log "  Infrastructure detected at localhost:8080."
  fi
fi

# ---------------------------------------------------------------------------
# Cleanup trap
# ---------------------------------------------------------------------------
infra_cleanup() {
  # Kill port-forward background processes
  if [[ ${#PF_PIDS[@]} -gt 0 ]]; then
    log ""
    log "Cleaning up..."
    for pid in "${PF_PIDS[@]}"; do
      kill "$pid" 2>/dev/null || true
    done
    info "port-forwards stopped"
  fi

  if [[ $INFRA_CLEANUP_NEEDED -eq 1 ]]; then
    if [[ $WITH_INFRA_COMPOSE -eq 1 ]]; then
      log "  Stopping compose stack..."
      (
        cd "$PROJECT_ROOT/poc/java-vertx-distributed"
        bash scripts/down.sh || true
      ) >> "$INFRA_LOG" 2>&1
    elif [[ $WITH_INFRA_K8S -eq 1 && $CLEANUP_K8S -eq 1 ]]; then
      log "  Tearing down k8s cluster (--cleanup-k8s)..."
      (
        cd "$PROJECT_ROOT/poc/k8s-local"
        bash scripts/down.sh --provider "${K8S_PROVIDER:-k3d}" || true
      ) >> "$INFRA_LOG" 2>&1
      info "cluster torn down"
    elif [[ $WITH_INFRA_K8S -eq 1 && $CLEANUP_K8S -eq 0 ]]; then
      info "cluster left running (use --cleanup-k8s to teardown)"
    fi
  fi
}
trap infra_cleanup EXIT

# ---------------------------------------------------------------------------
# Suite runners
# ---------------------------------------------------------------------------
TOTAL_START_NS="$(now_ns)"

# Results storage (parallel arrays — bash 3 compatible)
result_ids=()
result_status=()   # PASS FAIL SKIP
result_duration=() # seconds float
result_note=()

run_suite() {
  local id="$1"
  local suite_out="$OUT_DIR/$id"
  mkdir -p "$suite_out"

  local label
  label="$(suite_label "$id")"
  local needs_infra
  needs_infra="$(suite_needs_infra "$id")"

  # needs_infra=1: any infra (compose or k8s), needs_infra=2: k8s only
  if [[ "$needs_infra" -eq 1 && $INFRA_UP -eq 0 ]]; then
    echo "SKIP — infra not up; rerun with --with-infra-compose or --with-infra-k8s" \
      > "$suite_out/stdout.log"
    touch "$suite_out/stderr.log"
    echo "SKIP" > "$suite_out/exit-code"
    echo "SKIP"
    return
  fi
  if [[ "$needs_infra" -eq 2 && $WITH_INFRA_K8S -eq 0 ]]; then
    echo "SKIP — requires --with-infra-k8s" \
      > "$suite_out/stdout.log"
    touch "$suite_out/stderr.log"
    echo "SKIP" > "$suite_out/exit-code"
    echo "SKIP"
    return
  fi

  local start_ns
  start_ns="$(now_ns)"

  local exit_code=0
  case "$id" in
    arch)
      if [[ ! -f "$PROJECT_ROOT/tests/architecture/pom.xml" ]]; then
        echo "SKIP — tests/architecture/pom.xml not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      (cd "$PROJECT_ROOT/tests/architecture" && \
        timeout 600 mvn test \
          > "$suite_out/stdout.log" 2> "$suite_out/stderr.log") || exit_code=$?
      ;;
    cucumber)
      if [[ ! -f "$PROJECT_ROOT/tests/risk-engine-atdd/pom.xml" ]]; then
        echo "SKIP — tests/risk-engine-atdd/pom.xml not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      timeout 300 bash "$SCRIPT_DIR/atdd-bare.sh" \
        > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      ;;
    integration)
      if [[ ! -f "$PROJECT_ROOT/tests/integration/pom.xml" ]]; then
        echo "SKIP — tests/integration/pom.xml not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      if ! docker_available; then
        echo "SKIP — Docker daemon not running (required for Testcontainers)" \
          > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      (cd "$PROJECT_ROOT/tests/integration" && \
        timeout 600 mvn -Pintegration verify \
          > "$suite_out/stdout.log" 2> "$suite_out/stderr.log") || exit_code=$?
      ;;
    smoke)
      local smoke_bin="$PROJECT_ROOT/cli/risk-smoke/bin/risk-smoke"
      if [[ ! -x "$smoke_bin" ]]; then
        # Try to build it
        (cd "$PROJECT_ROOT/cli/risk-smoke" && make build) \
          > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      fi
      if [[ -x "$smoke_bin" ]]; then
        timeout 120 "$smoke_bin" --headless --no-file-report \
          >> "$suite_out/stdout.log" 2>> "$suite_out/stderr.log" || exit_code=$?
      else
        echo "SKIP — risk-smoke binary not found and build failed" >> "$suite_out/stdout.log"
        echo "SKIP"
        return
      fi
      ;;
    karate)
      if [[ ! -f "$PROJECT_ROOT/poc/java-vertx-distributed/scripts/atdd.sh" ]]; then
        echo "SKIP — poc/java-vertx-distributed/scripts/atdd.sh not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      timeout 600 bash "$PROJECT_ROOT/poc/java-vertx-distributed/scripts/atdd.sh" \
        > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      ;;
    bench-inproc)
      if [[ ! -f "$PROJECT_ROOT/bench/scripts/run-inprocess.sh" ]]; then
        echo "SKIP — bench/scripts/run-inprocess.sh not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      timeout 600 bash "$PROJECT_ROOT/bench/scripts/run-inprocess.sh" \
        > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      ;;
    bench-dist)
      if [[ ! -f "$PROJECT_ROOT/bench/scripts/run-distributed.sh" ]]; then
        echo "SKIP — bench/scripts/run-distributed.sh not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      timeout 600 bash "$PROJECT_ROOT/bench/scripts/run-distributed.sh" \
        > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      ;;
    k8s-smoke)
      # Smoke tests via Traefik Ingress — no port-forward, hits ingress directly.
      # Expects INGRESS_HOST env var or defaults to localhost (k3d exposes 80/443 on host).
      local ingress_host="${INGRESS_HOST:-localhost}"
      local smoke_bin="$PROJECT_ROOT/cli/risk-smoke/bin/risk-smoke"
      if [[ ! -x "$smoke_bin" ]]; then
        (cd "$PROJECT_ROOT/cli/risk-smoke" && make build) \
          > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      fi
      if [[ -x "$smoke_bin" ]]; then
        timeout 120 \
          env RISK_ENGINE_HOST="$ingress_host" RISK_ENGINE_PORT=80 \
          "$smoke_bin" --headless --no-file-report \
          >> "$suite_out/stdout.log" 2>> "$suite_out/stderr.log" || exit_code=$?
      else
        echo "SKIP — risk-smoke binary not found and build failed" >> "$suite_out/stdout.log"
        echo "SKIP"
        return
      fi
      ;;
    coverage-audit)
      local ca_script="$PROJECT_ROOT/.ai/scripts/coverage-audit.py"
      local ca_out="$PROJECT_ROOT/out/coverage-audit"
      if [[ ! -f "$ca_script" ]]; then
        echo "SKIP — .ai/scripts/coverage-audit.py not found" > "$suite_out/stdout.log"
        touch "$suite_out/stderr.log"
        echo "0" > "$suite_out/exit-code"
        echo "SKIP"
        return
      fi
      mkdir -p "$ca_out"
      python3 "$ca_script" all --report-md \
        > "$suite_out/stdout.log" 2> "$suite_out/stderr.log" || exit_code=$?
      # Copy latest report to suite output
      if [[ -L "$ca_out/latest" ]]; then
        cp "$ca_out/latest/coverage-audit.md" "$suite_out/coverage.md" 2>/dev/null || true
      fi
      ;;
    *)
      echo "Unknown suite: $id" >&2
      exit_code=1
      ;;
  esac

  echo "$exit_code" > "$suite_out/exit-code"

  # Link report if suite produced one in out/<suite>/latest/
  local latest_link="$PROJECT_ROOT/out/${id}/latest"
  if [[ -e "$latest_link" ]]; then
    ln -sfn "$latest_link" "$suite_out/suite-report"
  fi

  if [[ $exit_code -eq 0 ]]; then
    echo "PASS"
  else
    echo "FAIL"
  fi
}

# ---------------------------------------------------------------------------
# Run all suites
# ---------------------------------------------------------------------------
log ""
log "Test orchestrator — $(ts_iso)"
if [[ $WITH_INFRA_K8S -eq 1 ]]; then
  log "Mode: infra-k8s (provider: ${K8S_PROVIDER:-auto})"
elif [[ $WITH_INFRA_COMPOSE -eq 1 ]]; then
  log "Mode: infra-compose"
else
  log "Mode: no-infra"
fi
log ""
log "Running suites..."
log ""

idx=0
pass_count=0
fail_count=0
skip_count=0
total_dur_s=0

for suite_id in "${enabled_suites[@]}"; do
  idx=$((idx + 1))

  suite_start="$(now_ns)"
  status="$(run_suite "$suite_id")"
  suite_end="$(now_ns)"

  dur_ns=$(( suite_end - suite_start ))
  dur_s="$(awk "BEGIN{printf \"%.1f\", $dur_ns/1000000000}")"

  result_ids+=("$suite_id")
  result_status+=("$status")
  result_duration+=("$dur_s")

  # Read note (first line of stdout log as detail)
  note_file="$OUT_DIR/$suite_id/stdout.log"
  note=""
  if [[ -f "$note_file" ]]; then
    note="$(head -1 "$note_file" 2>/dev/null || true)"
    # Trim long lines
    note="${note:0:80}"
  fi
  result_note+=("$note")

  case "$status" in
    PASS) pass_count=$((pass_count + 1)) ;;
    FAIL) fail_count=$((fail_count + 1)) ;;
    SKIP) skip_count=$((skip_count + 1)) ;;
  esac

  # Accumulate duration
  total_dur_s="$(awk "BEGIN{printf \"%.1f\", $total_dur_s + $dur_s}")"

  printf "  [%d/%d] %-14s  %s  (%ss)\n" \
    "$idx" "${#enabled_suites[@]}" \
    "$suite_id" \
    "$status" \
    "$dur_s"
done

# ---------------------------------------------------------------------------
# Generate reports
# ---------------------------------------------------------------------------
generate_report() {
  local out="$1"
  local run_ts
  run_ts="$(ts_iso)"
  local git_sha
  git_sha="$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown")"

  # Build suite arrays for meta.json
  local suites_run=()
  local suites_skipped=()
  local i=0
  for sid in "${result_ids[@]}"; do
    if [[ "${result_status[$i]}" == "SKIP" ]]; then
      suites_skipped+=("$sid")
    else
      suites_run+=("$sid")
    fi
    i=$((i + 1))
  done

  # summary.md
  {
    echo "# Test Orchestrator Report"
    echo ""
    echo "**Run:** $run_ts"
    echo "**Host:** $(hostname)"
    echo "**Git SHA:** $git_sha"
    echo "**Mode:** $INFRA_MODE"
    [[ -n "$K8S_PROVIDER" ]] && echo "**K8s provider:** $K8S_PROVIDER"
    echo ""
    echo "## Summary"
    echo ""
    echo "| # | Suite | Status | Duration | Notes |"
    echo "|---|-------|--------|----------|-------|"
    local j=0
    for sid in "${result_ids[@]}"; do
      local s="${result_status[$j]}"
      local d="${result_duration[$j]}"
      local n="${result_note[$j]}"
      printf "| %d | %s | %s | %ss | %s |\n" \
        "$((j+1))" "$sid" "$s" "$d" "$n"
      j=$((j + 1))
    done
    echo ""
    echo "**Total:** ${pass_count} PASS · ${fail_count} FAIL · ${skip_count} SKIP · ${total_dur_s}s"
    echo ""
    echo "---"
    echo ""
    echo "_Generated by scripts/test-all.sh_"
  } > "$out/summary.md"

  # summary.txt
  {
    echo "Test Orchestrator Report — $run_ts"
    echo ""
    local j=0
    for sid in "${result_ids[@]}"; do
      printf "  %-16s  %s  (%ss)\n" \
        "$sid" "${result_status[$j]}" "${result_duration[$j]}"
      j=$((j + 1))
    done
    echo ""
    echo "Summary: ${pass_count} PASS · ${fail_count} FAIL · ${skip_count} SKIP · ${total_dur_s}s"
  } > "$out/summary.txt"

  # meta.json — extended with mode / provider / suites_run / suites_skipped
  {
    local sr_json=""
    for sid in "${suites_run[@]}"; do
      sr_json="${sr_json:+$sr_json, }\"$sid\""
    done
    local ss_json=""
    for sid in "${suites_skipped[@]}"; do
      ss_json="${ss_json:+$ss_json, }\"$sid\""
    done

    echo "{"
    echo "  \"timestamp\": \"$run_ts\","
    echo "  \"mode\": \"$INFRA_MODE\","
    if [[ -n "$K8S_PROVIDER" ]]; then
      echo "  \"provider\": \"$K8S_PROVIDER\","
    fi
    echo "  \"host\": \"$(hostname)\","
    echo "  \"git_sha\": \"$git_sha\","
    echo "  \"suites_run\": [$sr_json],"
    echo "  \"suites_skipped\": [$ss_json],"
    echo "  \"duration_total_seconds\": $total_dur_s,"
    echo "  \"flags\": {"
    echo "    \"headless\": $HEADLESS,"
    echo "    \"cleanup_k8s\": $CLEANUP_K8S,"
    echo "    \"only\": \"$ONLY_LIST\","
    echo "    \"skip\": \"$SKIP_LIST\""
    echo "  },"
    echo "  \"suites\": ["
    local j=0
    local last_idx=$(( ${#result_ids[@]} - 1 ))
    for sid in "${result_ids[@]}"; do
      local comma=""
      [[ $j -lt $last_idx ]] && comma=","
      echo "    {\"id\": \"$sid\", \"status\": \"${result_status[$j]}\", \"duration_s\": ${result_duration[$j]}}${comma}"
      j=$((j + 1))
    done
    echo "  ],"
    echo "  \"totals\": {\"pass\": $pass_count, \"fail\": $fail_count, \"skip\": $skip_count, \"total_s\": $total_dur_s}"
    echo "}"
  } > "$out/meta.json"
}

generate_report "$OUT_DIR"

# Symlink latest
ln -sfn "$OUT_DIR" "$OUT_BASE/latest" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Final summary console output
# ---------------------------------------------------------------------------
log ""
log "Summary: ${pass_count} PASS · ${fail_count} FAIL · ${skip_count} SKIP · ${total_dur_s}s total"
log "Report:  $OUT_DIR/summary.md"

if [[ $fail_count -gt 0 ]]; then
  log ""
  log "Failed suites — check logs under $OUT_DIR/<suite-id>/stderr.log"
  exit 1
fi
exit 0
