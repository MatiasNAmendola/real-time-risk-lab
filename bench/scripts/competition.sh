#!/usr/bin/env bash
# competition.sh — 4-way HTTP performance competition:
#   - no-vertx-clean-engine            (no-vertx-clean-engine)          port 8081
#   - vertx-monolith-inprocess         (vertx-monolith-inprocess controller)  port 8090
#   - vertx-layer-as-pod-http   (vertx-layer-as-pod-http controller-pod)        port 8180
#   - vertx-layer-as-pod-eventbus (controller-app)           port 8080
#
# All four receive the SAME workload via the shared distributed-bench JAR.
# Produces: out/competition/<ts>/{summary.md, summary.txt, comparison.json, comparison.csv}
#
# Usage:
#   ./scripts/competition.sh                        # 5000 req, 32 concurrency
#   ./scripts/competition.sh --requests 10000 --concurrency 64
#   ./scripts/competition.sh --quick                # 500 req, 8 concurrency (smoke)
#   ./scripts/competition.sh --skip-no-vertx-clean-engine            # skip no-vertx-clean-engine
#   ./scripts/competition.sh --skip-vertx-monolith-inprocess        # skip vertx-monolith-inprocess
#   ./scripts/competition.sh --skip-vertx-layer-as-pod-http             # skip vertx-layer-as-pod-http
#   ./scripts/competition.sh --skip-vertx-layer-as-pod-eventbus     # skip vertx-layer-as-pod-eventbus

set -euo pipefail

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_DIR}/.." && pwd)"

NO_VERTX_CLEAN_ENGINE_DIR="${REPO_ROOT}/poc/no-vertx-clean-engine"
DISTRIBUTED_JAR_DIR="${BENCH_DIR}/distributed-bench/build/libs"
DISTRIBUTED_JAR=""

JAVA="${JAVA:-java}"
JAVAC="${JAVAC:-javac}"

# Try Homebrew JDK on macOS
if [[ -x "/opt/homebrew/opt/openjdk/bin/java" ]]; then
  JAVA="/opt/homebrew/opt/openjdk/bin/java"
  JAVAC="/opt/homebrew/opt/openjdk/bin/javac"
fi

# ── Defaults ─────────────────────────────────────────────────────────────────
REQUESTS=5000
CONCURRENCY=32
WARMUP=500
SKIP_NO_VERTX_CLEAN_ENGINE=false
SKIP_VERTX_MONOLITH_INPROCESS=false
SKIP_VERTX_LAYER_AS_POD_HTTP=false
SKIP_VERTX_LAYER_AS_POD_EVENTBUS=false
NO_VERTX_CLEAN_ENGINE_PORT=8081
VERTX_MONOLITH_INPROCESS_PORT=8090
VERTX_LAYER_AS_POD_HTTP_PORT=8180
VERTX_LAYER_AS_POD_EVENTBUS_PORT=8080

# ── Arg parsing ──────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --requests)      REQUESTS="$2";      shift 2 ;;
    --concurrency)   CONCURRENCY="$2";   shift 2 ;;
    --warmup)        WARMUP="$2";        shift 2 ;;
    --quick)         REQUESTS=500; CONCURRENCY=8; WARMUP=100; shift ;;
    --skip-no-vertx-clean-engine)        SKIP_NO_VERTX_CLEAN_ENGINE=true;        shift ;;
    --skip-vertx-monolith-inprocess)    SKIP_VERTX_MONOLITH_INPROCESS=true;    shift ;;
    --skip-vertx-layer-as-pod-http)         SKIP_VERTX_LAYER_AS_POD_HTTP=true;         shift ;;
    --skip-vertx-layer-as-pod-eventbus) SKIP_VERTX_LAYER_AS_POD_EVENTBUS=true; shift ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

# ── Output dir ────────────────────────────────────────────────────────────────
TS="$(date +%Y%m%dT%H%M%S)"
OUT="${BENCH_DIR}/out/competition/${TS}"
mkdir -p "${OUT}"

log() { echo "[competition] $*"; }
die() { echo "[competition] ERROR: $*" >&2; exit 1; }

log "Output dir: ${OUT}"
log "Workload: requests=${REQUESTS}, concurrency=${CONCURRENCY}, warmup=${WARMUP}"


# ── Ensure distributed-bench shadowJar is built (Gradle) ──────────────────────
DISTRIBUTED_JAR="$(ls "${DISTRIBUTED_JAR_DIR}"/*.jar 2>/dev/null | grep -v -- '-sources\.jar$' | head -1 || true)"
if [[ -z "${DISTRIBUTED_JAR}" || ! -f "${DISTRIBUTED_JAR}" ]]; then
  log "distributed-bench JAR not found, building via Gradle..."
  (cd "${REPO_ROOT}" && ./gradlew :bench:distributed-bench:shadowJar -q) \
    || die "Failed to build distributed-bench. Run: ./gradlew :bench:distributed-bench:shadowJar"
  DISTRIBUTED_JAR="$(ls "${DISTRIBUTED_JAR_DIR}"/*.jar 2>/dev/null | grep -v -- '-sources\.jar$' | head -1 || true)"
  [[ -f "${DISTRIBUTED_JAR}" ]] || die "Build succeeded but no jar found in ${DISTRIBUTED_JAR_DIR}"
fi
log "distributed-bench JAR: ${DISTRIBUTED_JAR}"

# ── Helper: check HTTP endpoint ───────────────────────────────────────────────
wait_for_http() {
  local url="$1"
  local max_wait="${2:-30}"
  local elapsed=0
  while [[ "${elapsed}" -lt "${max_wait}" ]]; do
    if curl -sf --max-time 8 "${url}" > /dev/null 2>&1; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 1
}

# ── Step 1: Build no-vertx-clean-engine shadowJar (Gradle, includes all dep classes) ─────
NO_VERTX_CLEAN_ENGINE_JAR_DIR="${NO_VERTX_CLEAN_ENGINE_DIR}/build/libs"
NO_VERTX_CLEAN_ENGINE_JAR=""
if [[ "${SKIP_NO_VERTX_CLEAN_ENGINE}" == "false" ]]; then
  NO_VERTX_CLEAN_ENGINE_JAR="$(ls "${NO_VERTX_CLEAN_ENGINE_JAR_DIR}"/no-vertx-clean-engine.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${NO_VERTX_CLEAN_ENGINE_JAR}" || ! -f "${NO_VERTX_CLEAN_ENGINE_JAR}" ]]; then
    log "no-vertx-clean-engine shadowJar not found. Building via Gradle..."
    (cd "${REPO_ROOT}" && ./gradlew :poc:no-vertx-clean-engine:shadowJar -q) \
      || die "Failed to build no-vertx-clean-engine shadowJar."
    NO_VERTX_CLEAN_ENGINE_JAR="${NO_VERTX_CLEAN_ENGINE_JAR_DIR}/no-vertx-clean-engine.jar"
    [[ -f "${NO_VERTX_CLEAN_ENGINE_JAR}" ]] || die "shadowJar build succeeded but jar not found at ${NO_VERTX_CLEAN_ENGINE_JAR}"
  fi
  log "no-vertx-clean-engine jar: ${NO_VERTX_CLEAN_ENGINE_JAR}"
fi

# ── Step 2: Check pre-started services ───────────────────────────────────────
if [[ "${SKIP_VERTX_MONOLITH_INPROCESS}" == "false" ]]; then
  log "Checking vertx-monolith-inprocess at localhost:${VERTX_MONOLITH_INPROCESS_PORT}/healthz ..."
  if ! curl -sf --max-time 8 "http://localhost:${VERTX_MONOLITH_INPROCESS_PORT}/healthz" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: vertx-monolith-inprocess is not running." >&2
    echo "              Start it first: ./nx run vertx-monolith-inprocess" >&2
    echo "              Or skip with: --skip-vertx-monolith-inprocess" >&2
    echo "" >&2
    exit 1
  fi
  log "vertx-monolith-inprocess is up."
fi

if [[ "${SKIP_VERTX_LAYER_AS_POD_HTTP}" == "false" ]]; then
  log "Checking vertx-layer-as-pod-http at localhost:${VERTX_LAYER_AS_POD_HTTP_PORT}/health ..."
  if ! curl -sf --max-time 8 "http://localhost:${VERTX_LAYER_AS_POD_HTTP_PORT}/health" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: vertx-layer-as-pod-http controller-pod is not running." >&2
    echo "              Start it first: ./nx up vertx-layer-as-pod-http" >&2
    echo "              Or skip with: --skip-vertx-layer-as-pod-http" >&2
    echo "" >&2
    exit 1
  fi
  log "vertx-layer-as-pod-http is up."
fi

if [[ "${SKIP_VERTX_LAYER_AS_POD_EVENTBUS}" == "false" ]]; then
  log "Checking vertx-layer-as-pod-eventbus at localhost:${VERTX_LAYER_AS_POD_EVENTBUS_PORT}/health ..."
  if ! curl -sf --max-time 8 "http://localhost:${VERTX_LAYER_AS_POD_EVENTBUS_PORT}/health" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: vertx-layer-as-pod-eventbus stack is not running." >&2
    echo "              Start it first: ./nx up vertx-layer-as-pod-eventbus" >&2
    echo "              Or skip with: --skip-vertx-layer-as-pod-eventbus" >&2
    echo "" >&2
    exit 1
  fi
  log "vertx-layer-as-pod-eventbus is up."
fi

# ── Step 3: Start no-vertx-clean-engine HTTP server in background ───────────────────────
NO_VERTX_CLEAN_ENGINE_PID=""
if [[ "${SKIP_NO_VERTX_CLEAN_ENGINE}" == "false" ]]; then
  log "Starting no-vertx-clean-engine HTTP server on port ${NO_VERTX_CLEAN_ENGINE_PORT}..."
  RISK_HTTP_PORT="${NO_VERTX_CLEAN_ENGINE_PORT}" \
  "${JAVA}" -jar "${NO_VERTX_CLEAN_ENGINE_JAR}" \
    --port "${NO_VERTX_CLEAN_ENGINE_PORT}" \
    > "${OUT}/no-vertx-clean-engine.log" 2>&1 &
  NO_VERTX_CLEAN_ENGINE_PID=$!
  log "no-vertx-clean-engine PID=${NO_VERTX_CLEAN_ENGINE_PID}"

  log "Waiting for no-vertx-clean-engine to start (up to 30 s)..."
  if ! wait_for_http "http://localhost:${NO_VERTX_CLEAN_ENGINE_PORT}/healthz" 30; then
    kill "${NO_VERTX_CLEAN_ENGINE_PID}" 2>/dev/null || true
    die "no-vertx-clean-engine did not start within 30 s. Check ${OUT}/no-vertx-clean-engine.log"
  fi
  log "no-vertx-clean-engine is up."
fi

# ── Cleanup trap ─────────────────────────────────────────────────────────────
cleanup() {
  if [[ -n "${NO_VERTX_CLEAN_ENGINE_PID}" ]]; then
    log "Stopping no-vertx-clean-engine (PID ${NO_VERTX_CLEAN_ENGINE_PID})..."
    kill "${NO_VERTX_CLEAN_ENGINE_PID}" 2>/dev/null || true
    wait "${NO_VERTX_CLEAN_ENGINE_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ── Helper: run load gen phase ────────────────────────────────────────────────
run_phase() {
  local name="$1"
  local n="$2"
  local base_url="$3"
  local out_dir="$4"
  local risk_path="${5:-/risk}"
  log "  Phase [${name}]: ${n} requests -> ${base_url}${risk_path}"
  "${JAVA}" -jar "${DISTRIBUTED_JAR}" "${n}" "${CONCURRENCY}" "${base_url}" "${out_dir}" "${risk_path}" \
    || { log "  Phase ${name} failed (exit $?)"; return 1; }
}

# ── Step 4: Warmup ────────────────────────────────────────────────────────────
WARMUP_DIR="${OUT}/warmup"
mkdir -p "${WARMUP_DIR}"
log "--- Warmup phase (${WARMUP} reqs each, not measured) ---"
[[ "${SKIP_NO_VERTX_CLEAN_ENGINE}" == "false" ]]     && run_phase "warmup-no-vertx-clean-engine"     "${WARMUP}" "http://localhost:${NO_VERTX_CLEAN_ENGINE_PORT}"    "${WARMUP_DIR}" || true
[[ "${SKIP_VERTX_MONOLITH_INPROCESS}" == "false" ]] && run_phase "warmup-vertx-monolith-inprocess" "${WARMUP}" "http://localhost:${VERTX_MONOLITH_INPROCESS_PORT}" "${WARMUP_DIR}" || true
[[ "${SKIP_VERTX_LAYER_AS_POD_HTTP}" == "false" ]]      && run_phase "warmup-vertx-layer-as-pod-eventbus-layer-as-pod-http"      "${WARMUP}" "http://localhost:${VERTX_LAYER_AS_POD_HTTP_PORT}"     "${WARMUP_DIR}" "/risk/evaluate" || true
[[ "${SKIP_VERTX_LAYER_AS_POD_EVENTBUS}" == "false" ]] && run_phase "warmup-vertx-layer-as-pod-eventbus" "${WARMUP}" "http://localhost:${VERTX_LAYER_AS_POD_EVENTBUS_PORT}"  "${WARMUP_DIR}" || true

# ── Step 5: Measured run ──────────────────────────────────────────────────────
NO_VERTX_CLEAN_ENGINE_OUT="${OUT}/no-vertx-clean-engine-results"
VERTX_MONOLITH_INPROCESS_OUT="${OUT}/vertx-monolith-inprocess-results"
VERTX_LAYER_AS_POD_HTTP_OUT="${OUT}/vertx-layer-as-pod-http-results"
VERTX_LAYER_AS_POD_EVENTBUS_OUT="${OUT}/vertx-layer-as-pod-eventbus-results"
mkdir -p "${NO_VERTX_CLEAN_ENGINE_OUT}" "${VERTX_MONOLITH_INPROCESS_OUT}" "${VERTX_LAYER_AS_POD_HTTP_OUT}" "${VERTX_LAYER_AS_POD_EVENTBUS_OUT}"

log "--- Measured run: ${REQUESTS} reqs, ${CONCURRENCY} concurrency ---"

NO_VERTX_CLEAN_ENGINE_JSON=""
VERTX_MONOLITH_INPROCESS_JSON=""
VERTX_LAYER_AS_POD_HTTP_JSON=""
VERTX_LAYER_AS_POD_EVENTBUS_JSON=""

if [[ "${SKIP_NO_VERTX_CLEAN_ENGINE}" == "false" ]]; then
  run_phase "no-vertx-clean-engine" "${REQUESTS}" "http://localhost:${NO_VERTX_CLEAN_ENGINE_PORT}" "${NO_VERTX_CLEAN_ENGINE_OUT}"
  NO_VERTX_CLEAN_ENGINE_JSON="$(find "${NO_VERTX_CLEAN_ENGINE_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "no-vertx-clean-engine result: ${NO_VERTX_CLEAN_ENGINE_JSON}"
fi

if [[ "${SKIP_VERTX_MONOLITH_INPROCESS}" == "false" ]]; then
  run_phase "vertx-monolith-inprocess" "${REQUESTS}" "http://localhost:${VERTX_MONOLITH_INPROCESS_PORT}" "${VERTX_MONOLITH_INPROCESS_OUT}"
  VERTX_MONOLITH_INPROCESS_JSON="$(find "${VERTX_MONOLITH_INPROCESS_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "vertx-monolith-inprocess result: ${VERTX_MONOLITH_INPROCESS_JSON}"
fi

if [[ "${SKIP_VERTX_LAYER_AS_POD_HTTP}" == "false" ]]; then
  run_phase "vertx-layer-as-pod-http" "${REQUESTS}" "http://localhost:${VERTX_LAYER_AS_POD_HTTP_PORT}" "${VERTX_LAYER_AS_POD_HTTP_OUT}" "/risk/evaluate"
  VERTX_LAYER_AS_POD_HTTP_JSON="$(find "${VERTX_LAYER_AS_POD_HTTP_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "vertx-layer-as-pod-http result: ${VERTX_LAYER_AS_POD_HTTP_JSON}"
fi

if [[ "${SKIP_VERTX_LAYER_AS_POD_EVENTBUS}" == "false" ]]; then
  run_phase "vertx-layer-as-pod-eventbus" "${REQUESTS}" "http://localhost:${VERTX_LAYER_AS_POD_EVENTBUS_PORT}" "${VERTX_LAYER_AS_POD_EVENTBUS_OUT}"
  VERTX_LAYER_AS_POD_EVENTBUS_JSON="$(find "${VERTX_LAYER_AS_POD_EVENTBUS_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "vertx-layer-as-pod-eventbus result: ${VERTX_LAYER_AS_POD_EVENTBUS_JSON}"
fi

# ── Step 6: Parse results and build report ───────────────────────────────────
log "Building comparison report..."

# Extract metric from JSON using Python (available on macOS/Linux; no external deps)
extract() {
  local file="$1"
  local key="$2"
  local default="${3:--1}"
  if [[ -z "${file}" || ! -f "${file}" ]]; then
    echo "${default}"
    return
  fi
  python3 -c "
import json, sys
with open('${file}') as f:
    d = json.load(f)
val = d.get('${key}', ${default})
print(f'{val:.4f}' if isinstance(val, float) else val)
" 2>/dev/null || echo "${default}"
}

NO_VERTX_CLEAN_ENGINE_P50="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "p50Ms")"
NO_VERTX_CLEAN_ENGINE_P95="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "p95Ms")"
NO_VERTX_CLEAN_ENGINE_P99="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "p99Ms")"
NO_VERTX_CLEAN_ENGINE_P999="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "p999Ms")"
NO_VERTX_CLEAN_ENGINE_MAX="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "maxMs")"
NO_VERTX_CLEAN_ENGINE_RPS="$(extract "${NO_VERTX_CLEAN_ENGINE_JSON}" "throughputRps")"

VERTX_MONOLITH_INPROCESS_P50="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "p50Ms")"
VERTX_MONOLITH_INPROCESS_P95="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "p95Ms")"
VERTX_MONOLITH_INPROCESS_P99="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "p99Ms")"
VERTX_MONOLITH_INPROCESS_P999="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "p999Ms")"
VERTX_MONOLITH_INPROCESS_MAX="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "maxMs")"
VERTX_MONOLITH_INPROCESS_RPS="$(extract "${VERTX_MONOLITH_INPROCESS_JSON}" "throughputRps")"

VERTX_LAYER_AS_POD_HTTP_P50="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "p50Ms")"
VERTX_LAYER_AS_POD_HTTP_P95="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "p95Ms")"
VERTX_LAYER_AS_POD_HTTP_P99="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "p99Ms")"
VERTX_LAYER_AS_POD_HTTP_P999="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "p999Ms")"
VERTX_LAYER_AS_POD_HTTP_MAX="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "maxMs")"
VERTX_LAYER_AS_POD_HTTP_RPS="$(extract "${VERTX_LAYER_AS_POD_HTTP_JSON}" "throughputRps")"

VERTX_LAYER_AS_POD_EVENTBUS_P50="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "p50Ms")"
VERTX_LAYER_AS_POD_EVENTBUS_P95="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "p95Ms")"
VERTX_LAYER_AS_POD_EVENTBUS_P99="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "p99Ms")"
VERTX_LAYER_AS_POD_EVENTBUS_P999="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "p999Ms")"
VERTX_LAYER_AS_POD_EVENTBUS_MAX="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "maxMs")"
VERTX_LAYER_AS_POD_EVENTBUS_RPS="$(extract "${VERTX_LAYER_AS_POD_EVENTBUS_JSON}" "throughputRps")"

# Compute ratio (vertx-layer-as-pod-eventbus / no-vertx-clean-engine), default N/A if either is -1
ratio() {
  local a="$1"
  local b="$2"
  python3 -c "
a, b = ${a}, ${b}
if a <= 0 or b <= 0:
    print('N/A')
else:
    r = b / a
    print(f'{r:.1f}x')
" 2>/dev/null || echo "N/A"
}

ratio_rps() {
  local base="$1"
  local dist="$2"
  python3 -c "
base, dist = ${base}, ${dist}
if base <= 0 or dist <= 0:
    print('N/A')
else:
    r = base / dist
    print(f'{r:.1f}x')
" 2>/dev/null || echo "N/A"
}

fmt_ms() {
  local v="$1"
  python3 -c "
v = ${v}
if v < 0:
    print('N/A')
elif v < 1:
    print(f'{v*1000:.0f} us')
else:
    print(f'{v:.1f} ms')
" 2>/dev/null || echo "N/A"
}

fmt_rps() { python3 -c "v=${1}; print('N/A' if v<0 else f'{v:,.0f} req/s')" 2>/dev/null || echo "N/A"; }

N_P50_F="$(fmt_ms "${NO_VERTX_CLEAN_ENGINE_P50}")";    N_P95_F="$(fmt_ms "${NO_VERTX_CLEAN_ENGINE_P95}")"
N_P99_F="$(fmt_ms "${NO_VERTX_CLEAN_ENGINE_P99}")";    N_P999_F="$(fmt_ms "${NO_VERTX_CLEAN_ENGINE_P999}")"
N_MAX_F="$(fmt_ms "${NO_VERTX_CLEAN_ENGINE_MAX}")";    N_RPS_F="$(fmt_rps "${NO_VERTX_CLEAN_ENGINE_RPS}")"

MIP_P50_F="$(fmt_ms "${VERTX_MONOLITH_INPROCESS_P50}")"; MIP_P95_F="$(fmt_ms "${VERTX_MONOLITH_INPROCESS_P95}")"
MIP_P99_F="$(fmt_ms "${VERTX_MONOLITH_INPROCESS_P99}")"; MIP_P999_F="$(fmt_ms "${VERTX_MONOLITH_INPROCESS_P999}")"
MIP_MAX_F="$(fmt_ms "${VERTX_MONOLITH_INPROCESS_MAX}")"; MIP_RPS_F="$(fmt_rps "${VERTX_MONOLITH_INPROCESS_RPS}")"

H_P50_F="$(fmt_ms "${VERTX_LAYER_AS_POD_HTTP_P50}")";    H_P95_F="$(fmt_ms "${VERTX_LAYER_AS_POD_HTTP_P95}")"
H_P99_F="$(fmt_ms "${VERTX_LAYER_AS_POD_HTTP_P99}")";    H_P999_F="$(fmt_ms "${VERTX_LAYER_AS_POD_HTTP_P999}")"
H_MAX_F="$(fmt_ms "${VERTX_LAYER_AS_POD_HTTP_MAX}")";    H_RPS_F="$(fmt_rps "${VERTX_LAYER_AS_POD_HTTP_RPS}")"

E_P50_F="$(fmt_ms "${VERTX_LAYER_AS_POD_EVENTBUS_P50}")";   E_P95_F="$(fmt_ms "${VERTX_LAYER_AS_POD_EVENTBUS_P95}")"
E_P99_F="$(fmt_ms "${VERTX_LAYER_AS_POD_EVENTBUS_P99}")";   E_P999_F="$(fmt_ms "${VERTX_LAYER_AS_POD_EVENTBUS_P999}")"
E_MAX_F="$(fmt_ms "${VERTX_LAYER_AS_POD_EVENTBUS_MAX}")";   E_RPS_F="$(fmt_rps "${VERTX_LAYER_AS_POD_EVENTBUS_RPS}")"

ISO_TS="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date +%Y-%m-%dT%H:%M:%SZ)"
HW_INFO="$(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "unknown CPU"), $(sysctl -n hw.memsize 2>/dev/null | python3 -c 'import sys; v=int(sys.stdin.read()); print(f"{v//1073741824} GB")' 2>/dev/null || echo "unknown RAM")"
JDK_VER="$("${JAVA}" -version 2>&1 | head -1 | sed 's/openjdk version /JDK /;s/"//g' || echo "unknown JDK")"
OS_VER="$(uname -rs 2>/dev/null || echo "unknown OS")"

# ── summary.md ───────────────────────────────────────────────────────────────
cat > "${OUT}/summary.md" <<MDEOF
# Performance competition (4 architectures) — ${ISO_TS}

**Workload**: ${REQUESTS} requests, ${CONCURRENCY} concurrency, ${WARMUP} warmup each.
**Payload mix**: amounts random uniform from {1000, 5000, 15000, 50000, 150000, 200000, 500000} cents.
**Hardware**: ${HW_INFO}, ${JDK_VER}, OS ${OS_VER}.

## Latency side-by-side

| Metric | no-vertx-clean-engine | vertx-monolith-inprocess | vertx-layer-as-pod-http | vertx-layer-as-pod-eventbus |
|---|---:|---:|---:|---:|
| p50       | ${N_P50_F}  | ${MIP_P50_F}  | ${H_P50_F}  | ${E_P50_F}  |
| p95       | ${N_P95_F}  | ${MIP_P95_F}  | ${H_P95_F}  | ${E_P95_F}  |
| p99       | ${N_P99_F}  | ${MIP_P99_F}  | ${H_P99_F}  | ${E_P99_F}  |
| p99.9     | ${N_P999_F} | ${MIP_P999_F} | ${H_P999_F} | ${E_P999_F} |
| max       | ${N_MAX_F}  | ${MIP_MAX_F}  | ${H_MAX_F}  | ${E_MAX_F}  |
| throughput| ${N_RPS_F}  | ${MIP_RPS_F}  | ${H_RPS_F}  | ${E_RPS_F}  |

## Lectura arquitectonica

- no-vertx-clean-engine: 1 JVM, metodos directos + HTTP server minimal. Latencia base de referencia.
- vertx-monolith-inprocess: mismo dominio, Vert.x in-process + Postgres + Valkey. Stack moderno, un proceso.
- vertx-layer-as-pod-http: 3 pods HTTP. Cada hop suma RTT. tokens en headers (simples, auditables).
- vertx-layer-as-pod-eventbus: 4 pods event bus binario TCP (Hazelcast). Mas overhead de coordinacion, mas isolation.

## Frase de discusión técnica

> "Cuatro arquitecturas, mismo problema. El delta de latencia entre no-vertx-clean-engine y vertx-layer-as-pod-eventbus es el
> costo de separar capas en pods. vertx-layer-as-pod-http muestra que HTTP+tokens es comprensible para cualquier
> equipo; vertx-layer-as-pod-eventbus muestra que el EventBus binario te da isolation real pero a mayor complejidad."

## Archivos de evidencia

- no-vertx-clean-engine-results/, vertx-monolith-inprocess-results/, vertx-layer-as-pod-http-results/, vertx-layer-as-pod-eventbus-results/ — metricas crudas
- comparison.json — metricas unificadas
- comparison.csv  — tabuladas
MDEOF

# ── comparison.csv ────────────────────────────────────────────────────────────
cat > "${OUT}/comparison.csv" <<CSVEOF
metric,no_vertx_clean_engine_ms,vertx_monolith_inprocess_ms,vertx_layer_as_pod_http_ms,vertx_layer_as_pod_eventbus_ms
p50,${NO_VERTX_CLEAN_ENGINE_P50},${VERTX_MONOLITH_INPROCESS_P50},${VERTX_LAYER_AS_POD_HTTP_P50},${VERTX_LAYER_AS_POD_EVENTBUS_P50}
p95,${NO_VERTX_CLEAN_ENGINE_P95},${VERTX_MONOLITH_INPROCESS_P95},${VERTX_LAYER_AS_POD_HTTP_P95},${VERTX_LAYER_AS_POD_EVENTBUS_P95}
p99,${NO_VERTX_CLEAN_ENGINE_P99},${VERTX_MONOLITH_INPROCESS_P99},${VERTX_LAYER_AS_POD_HTTP_P99},${VERTX_LAYER_AS_POD_EVENTBUS_P99}
p999,${NO_VERTX_CLEAN_ENGINE_P999},${VERTX_MONOLITH_INPROCESS_P999},${VERTX_LAYER_AS_POD_HTTP_P999},${VERTX_LAYER_AS_POD_EVENTBUS_P999}
max,${NO_VERTX_CLEAN_ENGINE_MAX},${VERTX_MONOLITH_INPROCESS_MAX},${VERTX_LAYER_AS_POD_HTTP_MAX},${VERTX_LAYER_AS_POD_EVENTBUS_MAX}
throughput_rps,${NO_VERTX_CLEAN_ENGINE_RPS},${VERTX_MONOLITH_INPROCESS_RPS},${VERTX_LAYER_AS_POD_HTTP_RPS},${VERTX_LAYER_AS_POD_EVENTBUS_RPS}
CSVEOF

# ── comparison.json ──────────────────────────────────────────────────────────
python3 - <<PYEOF > "${OUT}/comparison.json"
import json
data = {
    "generatedAt": "${ISO_TS}",
    "workload": {
        "requests": ${REQUESTS},
        "concurrency": ${CONCURRENCY},
        "warmup": ${WARMUP}
    },
    "no_vertx_clean_engine": {
        "p50Ms":  ${NO_VERTX_CLEAN_ENGINE_P50}, "p95Ms":  ${NO_VERTX_CLEAN_ENGINE_P95}, "p99Ms":  ${NO_VERTX_CLEAN_ENGINE_P99},
        "p999Ms": ${NO_VERTX_CLEAN_ENGINE_P999}, "maxMs": ${NO_VERTX_CLEAN_ENGINE_MAX}, "throughputRps": ${NO_VERTX_CLEAN_ENGINE_RPS}
    },
    "vertx_monolith_inprocess": {
        "p50Ms":  ${VERTX_MONOLITH_INPROCESS_P50}, "p95Ms":  ${VERTX_MONOLITH_INPROCESS_P95}, "p99Ms":  ${VERTX_MONOLITH_INPROCESS_P99},
        "p999Ms": ${VERTX_MONOLITH_INPROCESS_P999}, "maxMs": ${VERTX_MONOLITH_INPROCESS_MAX}, "throughputRps": ${VERTX_MONOLITH_INPROCESS_RPS}
    },
    "vertx_layer_as_pod_http": {
        "p50Ms":  ${VERTX_LAYER_AS_POD_HTTP_P50}, "p95Ms":  ${VERTX_LAYER_AS_POD_HTTP_P95}, "p99Ms":  ${VERTX_LAYER_AS_POD_HTTP_P99},
        "p999Ms": ${VERTX_LAYER_AS_POD_HTTP_P999}, "maxMs": ${VERTX_LAYER_AS_POD_HTTP_MAX}, "throughputRps": ${VERTX_LAYER_AS_POD_HTTP_RPS}
    },
    "vertx_layer_as_pod_eventbus": {
        "p50Ms":  ${VERTX_LAYER_AS_POD_EVENTBUS_P50}, "p95Ms":  ${VERTX_LAYER_AS_POD_EVENTBUS_P95}, "p99Ms":  ${VERTX_LAYER_AS_POD_EVENTBUS_P99},
        "p999Ms": ${VERTX_LAYER_AS_POD_EVENTBUS_P999}, "maxMs": ${VERTX_LAYER_AS_POD_EVENTBUS_MAX}, "throughputRps": ${VERTX_LAYER_AS_POD_EVENTBUS_RPS}
    }
}
print(json.dumps(data, indent=2))
PYEOF

# ── summary.txt ───────────────────────────────────────────────────────────────
{
  echo "========================================================================"
  echo "Performance Competition (4 architectures) — ${ISO_TS}"
  echo "Workload: ${REQUESTS} req, ${CONCURRENCY} concurrency, ${WARMUP} warmup"
  echo "Hardware: ${HW_INFO}"
  echo "JDK: ${JDK_VER}  OS: ${OS_VER}"
  echo "========================================================================"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "Metric" "no-vertx-clean-engine" "vertx-monolith-inprocess" "vertx-layer-as-pod-http" "vertx-layer-as-pod-eventbus"
  echo "------------------------------------------------------------------------"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p50"        "${N_P50_F}"  "${MIP_P50_F}"  "${H_P50_F}"  "${E_P50_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p95"        "${N_P95_F}"  "${MIP_P95_F}"  "${H_P95_F}"  "${E_P95_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p99"        "${N_P99_F}"  "${MIP_P99_F}"  "${H_P99_F}"  "${E_P99_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p99.9"      "${N_P999_F}" "${MIP_P999_F}" "${H_P999_F}" "${E_P999_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "max"        "${N_MAX_F}"  "${MIP_MAX_F}"  "${H_MAX_F}"  "${E_MAX_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "throughput" "${N_RPS_F}"  "${MIP_RPS_F}"  "${H_RPS_F}"  "${E_RPS_F}"
  echo "========================================================================"
} > "${OUT}/summary.txt"

cat "${OUT}/summary.txt"

# ── Step 7: Optional plot ─────────────────────────────────────────────────────
PLOT_FILE="${OUT}/latency-comparison.png"
if command -v python3 &>/dev/null; then
  if python3 - <<PLOTEOF 2>/dev/null; then log "Plot written: ${PLOT_FILE}"; else log "matplotlib not available, skipping plot."; fi
import sys
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import numpy as np

    metrics = ['p50', 'p95', 'p99', 'p999']
    no_eventbus_vals  = [${NO_VERTX_CLEAN_ENGINE_P50},  ${NO_VERTX_CLEAN_ENGINE_P95},  ${NO_VERTX_CLEAN_ENGINE_P99},  ${NO_VERTX_CLEAN_ENGINE_P999}]
    eventbus_vals = [${VERTX_LAYER_AS_POD_EVENTBUS_P50}, ${VERTX_LAYER_AS_POD_EVENTBUS_P95}, ${VERTX_LAYER_AS_POD_EVENTBUS_P99}, ${VERTX_LAYER_AS_POD_EVENTBUS_P999}]

    # Only plot if we have real values
    if any(v < 0 for v in no_eventbus_vals + eventbus_vals):
        sys.exit(0)

    x = np.arange(len(metrics))
    width = 0.35

    fig, ax = plt.subplots(figsize=(9, 5))
    bars1 = ax.bar(x - width/2, no_eventbus_vals,  width, label='no-vertx-clean-engine (HTTP)', color='#2196F3')
    bars2 = ax.bar(x + width/2, eventbus_vals, width, label='vertx-layer-as-pod-eventbus', color='#FF5722')

    ax.set_ylabel('Latency (ms)')
    ax.set_title('Performance Competition: no-vertx-clean-engine vs vertx-layer-as-pod-eventbus\n(${REQUESTS} requests, concurrency=${CONCURRENCY})')
    ax.set_xticks(x)
    ax.set_xticklabels(metrics)
    ax.legend()
    ax.bar_label(bars1, padding=3, fmt='%.1f ms')
    ax.bar_label(bars2, padding=3, fmt='%.1f ms')
    ax.set_yscale('log')
    ax.set_ylabel('Latency ms (log scale)')

    fig.tight_layout()
    plt.savefig('${PLOT_FILE}', dpi=150)
    plt.close()
except ImportError:
    sys.exit(1)
PLOTEOF
elif command -v gnuplot &>/dev/null; then
  python3 - <<CSVPLOT 2>/dev/null || true
# gnuplot fallback — write script and run
script = """
set terminal png size 900,500 background '#ffffff'
set output '${PLOT_FILE}'
set title 'Performance Competition: no-vertx-clean-engine vs vertx-layer-as-pod-eventbus'
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set boxwidth 0.9
set xtics ('p50' 0, 'p95' 1, 'p99' 2, 'p999' 3)
set ylabel 'Latency (ms)'
set logscale y
plot '-' using 2:xtic(1) title 'no-vertx-clean-engine', '' using 2:xtic(1) title 'vertx-layer-as-pod-eventbus'
p50 ${NO_VERTX_CLEAN_ENGINE_P50}
p95 ${NO_VERTX_CLEAN_ENGINE_P95}
p99 ${NO_VERTX_CLEAN_ENGINE_P99}
p999 ${NO_VERTX_CLEAN_ENGINE_P999}
e
p50 ${VERTX_LAYER_AS_POD_EVENTBUS_P50}
p95 ${VERTX_LAYER_AS_POD_EVENTBUS_P95}
p99 ${VERTX_LAYER_AS_POD_EVENTBUS_P99}
p999 ${VERTX_LAYER_AS_POD_EVENTBUS_P999}
e
"""
with open('/tmp/competition_plot.gnuplot', 'w') as f:
    f.write(script)
import subprocess
subprocess.run(['gnuplot', '/tmp/competition_plot.gnuplot'], check=True)
CSVPLOT
fi

log "Done. Results in: ${OUT}"
log "  summary.md     -> ${OUT}/summary.md"
log "  comparison.csv -> ${OUT}/comparison.csv"
log "  comparison.json-> ${OUT}/comparison.json"
