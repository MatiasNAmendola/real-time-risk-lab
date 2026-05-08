#!/usr/bin/env bash
# competition.sh — 4-way HTTP performance competition:
#   - bare-javac            (java-risk-engine)          port 8081
#   - java-monolith         (java-monolith controller)  port 8090
#   - vertx-risk-platform   (vrp-controller-pod)        port 8180
#   - java-vertx-distributed (controller-app)           port 8080
#
# All four receive the SAME workload via the shared distributed-bench JAR.
# Produces: out/competition/<ts>/{summary.md, summary.txt, comparison.json, comparison.csv}
#
# Usage:
#   ./scripts/competition.sh                        # 5000 req, 32 concurrency
#   ./scripts/competition.sh --requests 10000 --concurrency 64
#   ./scripts/competition.sh --quick                # 500 req, 8 concurrency (smoke)
#   ./scripts/competition.sh --skip-bare            # skip bare-javac
#   ./scripts/competition.sh --skip-monolith        # skip java-monolith
#   ./scripts/competition.sh --skip-vrp             # skip vertx-risk-platform
#   ./scripts/competition.sh --skip-distributed     # skip java-vertx-distributed

set -euo pipefail

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_DIR}/.." && pwd)"

BARE_ENGINE_DIR="${REPO_ROOT}/poc/java-risk-engine"
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
SKIP_BARE=false
SKIP_MONOLITH=false
SKIP_VRP=false
SKIP_DISTRIBUTED=false
BARE_PORT=8081
MONOLITH_PORT=8090
VRP_PORT=8180
VERTX_PORT=8080

# ── Arg parsing ──────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --requests)      REQUESTS="$2";      shift 2 ;;
    --concurrency)   CONCURRENCY="$2";   shift 2 ;;
    --warmup)        WARMUP="$2";        shift 2 ;;
    --quick)         REQUESTS=500; CONCURRENCY=8; WARMUP=100; shift ;;
    --skip-bare)        SKIP_BARE=true;        shift ;;
    --skip-monolith)    SKIP_MONOLITH=true;    shift ;;
    --skip-vrp)         SKIP_VRP=true;         shift ;;
    --skip-distributed) SKIP_DISTRIBUTED=true; shift ;;
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

# ── Step 1: Build bare-javac shadowJar (Gradle, includes all dep classes) ─────
BARE_JAR_DIR="${BARE_ENGINE_DIR}/build/libs"
BARE_JAR=""
if [[ "${SKIP_BARE}" == "false" ]]; then
  BARE_JAR="$(ls "${BARE_JAR_DIR}"/java-risk-engine.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${BARE_JAR}" || ! -f "${BARE_JAR}" ]]; then
    log "bare-javac shadowJar not found. Building via Gradle..."
    (cd "${REPO_ROOT}" && ./gradlew :poc:java-risk-engine:shadowJar -q) \
      || die "Failed to build java-risk-engine shadowJar."
    BARE_JAR="${BARE_JAR_DIR}/java-risk-engine.jar"
    [[ -f "${BARE_JAR}" ]] || die "shadowJar build succeeded but jar not found at ${BARE_JAR}"
  fi
  log "bare-javac jar: ${BARE_JAR}"
fi

# ── Step 2: Check pre-started services ───────────────────────────────────────
if [[ "${SKIP_MONOLITH}" == "false" ]]; then
  log "Checking java-monolith at localhost:${MONOLITH_PORT}/healthz ..."
  if ! curl -sf --max-time 8 "http://localhost:${MONOLITH_PORT}/healthz" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: java-monolith is not running." >&2
    echo "              Start it first: ./nx run java-monolith" >&2
    echo "              Or skip with: --skip-monolith" >&2
    echo "" >&2
    exit 1
  fi
  log "java-monolith is up."
fi

if [[ "${SKIP_VRP}" == "false" ]]; then
  log "Checking vertx-risk-platform at localhost:${VRP_PORT}/health ..."
  if ! curl -sf --max-time 8 "http://localhost:${VRP_PORT}/health" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: vertx-risk-platform controller-pod is not running." >&2
    echo "              Start it first: ./nx up vertx-platform" >&2
    echo "              Or skip with: --skip-vrp" >&2
    echo "" >&2
    exit 1
  fi
  log "vertx-risk-platform is up."
fi

if [[ "${SKIP_DISTRIBUTED}" == "false" ]]; then
  log "Checking java-vertx-distributed at localhost:${VERTX_PORT}/health ..."
  if ! curl -sf --max-time 8 "http://localhost:${VERTX_PORT}/health" > /dev/null 2>&1; then
    echo "" >&2
    echo "[competition] ERROR: java-vertx-distributed stack is not running." >&2
    echo "              Start it first: ./nx up vertx" >&2
    echo "              Or skip with: --skip-distributed" >&2
    echo "" >&2
    exit 1
  fi
  log "java-vertx-distributed is up."
fi

# ── Step 3: Start bare-javac HTTP server in background ───────────────────────
BARE_PID=""
if [[ "${SKIP_BARE}" == "false" ]]; then
  log "Starting bare-javac HTTP server on port ${BARE_PORT}..."
  RISK_HTTP_PORT="${BARE_PORT}" \
  "${JAVA}" -jar "${BARE_JAR}" \
    --port "${BARE_PORT}" \
    > "${OUT}/bare.log" 2>&1 &
  BARE_PID=$!
  log "bare-javac PID=${BARE_PID}"

  log "Waiting for bare-javac to start (up to 30 s)..."
  if ! wait_for_http "http://localhost:${BARE_PORT}/healthz" 30; then
    kill "${BARE_PID}" 2>/dev/null || true
    die "bare-javac did not start within 30 s. Check ${OUT}/bare.log"
  fi
  log "bare-javac is up."
fi

# ── Cleanup trap ─────────────────────────────────────────────────────────────
cleanup() {
  if [[ -n "${BARE_PID}" ]]; then
    log "Stopping bare-javac (PID ${BARE_PID})..."
    kill "${BARE_PID}" 2>/dev/null || true
    wait "${BARE_PID}" 2>/dev/null || true
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
[[ "${SKIP_BARE}" == "false" ]]     && run_phase "warmup-bare"     "${WARMUP}" "http://localhost:${BARE_PORT}"    "${WARMUP_DIR}" || true
[[ "${SKIP_MONOLITH}" == "false" ]] && run_phase "warmup-monolith" "${WARMUP}" "http://localhost:${MONOLITH_PORT}" "${WARMUP_DIR}" || true
[[ "${SKIP_VRP}" == "false" ]]      && run_phase "warmup-vrp"      "${WARMUP}" "http://localhost:${VRP_PORT}"     "${WARMUP_DIR}" "/risk/evaluate" || true
[[ "${SKIP_DISTRIBUTED}" == "false" ]] && run_phase "warmup-vertx" "${WARMUP}" "http://localhost:${VERTX_PORT}"  "${WARMUP_DIR}" || true

# ── Step 5: Measured run ──────────────────────────────────────────────────────
BARE_OUT="${OUT}/bare-results"
MONOLITH_OUT="${OUT}/monolith-results"
VRP_OUT="${OUT}/vrp-results"
VERTX_OUT="${OUT}/vertx-results"
mkdir -p "${BARE_OUT}" "${MONOLITH_OUT}" "${VRP_OUT}" "${VERTX_OUT}"

log "--- Measured run: ${REQUESTS} reqs, ${CONCURRENCY} concurrency ---"

BARE_JSON=""
MONOLITH_JSON=""
VRP_JSON=""
VERTX_JSON=""

if [[ "${SKIP_BARE}" == "false" ]]; then
  run_phase "bare" "${REQUESTS}" "http://localhost:${BARE_PORT}" "${BARE_OUT}"
  BARE_JSON="$(find "${BARE_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "bare-javac result: ${BARE_JSON}"
fi

if [[ "${SKIP_MONOLITH}" == "false" ]]; then
  run_phase "monolith" "${REQUESTS}" "http://localhost:${MONOLITH_PORT}" "${MONOLITH_OUT}"
  MONOLITH_JSON="$(find "${MONOLITH_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "java-monolith result: ${MONOLITH_JSON}"
fi

if [[ "${SKIP_VRP}" == "false" ]]; then
  run_phase "vrp" "${REQUESTS}" "http://localhost:${VRP_PORT}" "${VRP_OUT}" "/risk/evaluate"
  VRP_JSON="$(find "${VRP_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "vertx-risk-platform result: ${VRP_JSON}"
fi

if [[ "${SKIP_DISTRIBUTED}" == "false" ]]; then
  run_phase "vertx" "${REQUESTS}" "http://localhost:${VERTX_PORT}" "${VERTX_OUT}"
  VERTX_JSON="$(find "${VERTX_OUT}" -maxdepth 1 -name '*.json' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)"
  log "java-vertx-distributed result: ${VERTX_JSON}"
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

BARE_P50="$(extract "${BARE_JSON}" "p50Ms")"
BARE_P95="$(extract "${BARE_JSON}" "p95Ms")"
BARE_P99="$(extract "${BARE_JSON}" "p99Ms")"
BARE_P999="$(extract "${BARE_JSON}" "p999Ms")"
BARE_MAX="$(extract "${BARE_JSON}" "maxMs")"
BARE_RPS="$(extract "${BARE_JSON}" "throughputRps")"

MONOLITH_P50="$(extract "${MONOLITH_JSON}" "p50Ms")"
MONOLITH_P95="$(extract "${MONOLITH_JSON}" "p95Ms")"
MONOLITH_P99="$(extract "${MONOLITH_JSON}" "p99Ms")"
MONOLITH_P999="$(extract "${MONOLITH_JSON}" "p999Ms")"
MONOLITH_MAX="$(extract "${MONOLITH_JSON}" "maxMs")"
MONOLITH_RPS="$(extract "${MONOLITH_JSON}" "throughputRps")"

VRP_P50="$(extract "${VRP_JSON}" "p50Ms")"
VRP_P95="$(extract "${VRP_JSON}" "p95Ms")"
VRP_P99="$(extract "${VRP_JSON}" "p99Ms")"
VRP_P999="$(extract "${VRP_JSON}" "p999Ms")"
VRP_MAX="$(extract "${VRP_JSON}" "maxMs")"
VRP_RPS="$(extract "${VRP_JSON}" "throughputRps")"

VERTX_P50="$(extract "${VERTX_JSON}" "p50Ms")"
VERTX_P95="$(extract "${VERTX_JSON}" "p95Ms")"
VERTX_P99="$(extract "${VERTX_JSON}" "p99Ms")"
VERTX_P999="$(extract "${VERTX_JSON}" "p999Ms")"
VERTX_MAX="$(extract "${VERTX_JSON}" "maxMs")"
VERTX_RPS="$(extract "${VERTX_JSON}" "throughputRps")"

# Compute ratio (vertx / bare), default N/A if either is -1
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

B_P50_F="$(fmt_ms "${BARE_P50}")";    B_P95_F="$(fmt_ms "${BARE_P95}")"
B_P99_F="$(fmt_ms "${BARE_P99}")";    B_P999_F="$(fmt_ms "${BARE_P999}")"
B_MAX_F="$(fmt_ms "${BARE_MAX}")";    B_RPS_F="$(fmt_rps "${BARE_RPS}")"

M_P50_F="$(fmt_ms "${MONOLITH_P50}")"; M_P95_F="$(fmt_ms "${MONOLITH_P95}")"
M_P99_F="$(fmt_ms "${MONOLITH_P99}")"; M_P999_F="$(fmt_ms "${MONOLITH_P999}")"
M_MAX_F="$(fmt_ms "${MONOLITH_MAX}")"; M_RPS_F="$(fmt_rps "${MONOLITH_RPS}")"

P_P50_F="$(fmt_ms "${VRP_P50}")";    P_P95_F="$(fmt_ms "${VRP_P95}")"
P_P99_F="$(fmt_ms "${VRP_P99}")";    P_P999_F="$(fmt_ms "${VRP_P999}")"
P_MAX_F="$(fmt_ms "${VRP_MAX}")";    P_RPS_F="$(fmt_rps "${VRP_RPS}")"

V_P50_F="$(fmt_ms "${VERTX_P50}")";   V_P95_F="$(fmt_ms "${VERTX_P95}")"
V_P99_F="$(fmt_ms "${VERTX_P99}")";   V_P999_F="$(fmt_ms "${VERTX_P999}")"
V_MAX_F="$(fmt_ms "${VERTX_MAX}")";   V_RPS_F="$(fmt_rps "${VERTX_RPS}")"

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

| Metric | bare-javac | java-monolith | vrp (HTTP+tokens) | vertx-distributed |
|---|---:|---:|---:|---:|
| p50       | ${B_P50_F}  | ${M_P50_F}  | ${P_P50_F}  | ${V_P50_F}  |
| p95       | ${B_P95_F}  | ${M_P95_F}  | ${P_P95_F}  | ${V_P95_F}  |
| p99       | ${B_P99_F}  | ${M_P99_F}  | ${P_P99_F}  | ${V_P99_F}  |
| p99.9     | ${B_P999_F} | ${M_P999_F} | ${P_P999_F} | ${V_P999_F} |
| max       | ${B_MAX_F}  | ${M_MAX_F}  | ${P_MAX_F}  | ${V_MAX_F}  |
| throughput| ${B_RPS_F}  | ${M_RPS_F}  | ${P_RPS_F}  | ${V_RPS_F}  |

## Lectura arquitectonica

- bare-javac: 1 JVM, metodos directos + HTTP server minimal. Latencia base de referencia.
- java-monolith: mismo dominio, Vert.x in-process + Postgres + Valkey. Stack moderno, un proceso.
- vertx-risk-platform: 3 pods HTTP. Cada hop suma RTT. tokens en headers (simples, auditables).
- vertx-distributed: 4 pods event bus binario TCP (Hazelcast). Mas overhead de coordinacion, mas isolation.

## Frase de entrevista

> "Cuatro arquitecturas, mismo problema. El delta de latencia entre bare y distributed es el
> costo de separar capas en pods. vrp muestra que HTTP+tokens es comprensible para cualquier
> equipo; distributed muestra que el event bus binario te da isolation real pero a mayor complejidad."

## Archivos de evidencia

- bare-results/, monolith-results/, vrp-results/, vertx-results/ — metricas crudas
- comparison.json — metricas unificadas
- comparison.csv  — tabuladas
MDEOF

# ── comparison.csv ────────────────────────────────────────────────────────────
cat > "${OUT}/comparison.csv" <<CSVEOF
metric,bare_javac_ms,java_monolith_ms,vrp_ms,vertx_distributed_ms
p50,${BARE_P50},${MONOLITH_P50},${VRP_P50},${VERTX_P50}
p95,${BARE_P95},${MONOLITH_P95},${VRP_P95},${VERTX_P95}
p99,${BARE_P99},${MONOLITH_P99},${VRP_P99},${VERTX_P99}
p999,${BARE_P999},${MONOLITH_P999},${VRP_P999},${VERTX_P999}
max,${BARE_MAX},${MONOLITH_MAX},${VRP_MAX},${VERTX_MAX}
throughput_rps,${BARE_RPS},${MONOLITH_RPS},${VRP_RPS},${VERTX_RPS}
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
    "bare_javac": {
        "p50Ms":  ${BARE_P50}, "p95Ms":  ${BARE_P95}, "p99Ms":  ${BARE_P99},
        "p999Ms": ${BARE_P999}, "maxMs": ${BARE_MAX}, "throughputRps": ${BARE_RPS}
    },
    "java_monolith": {
        "p50Ms":  ${MONOLITH_P50}, "p95Ms":  ${MONOLITH_P95}, "p99Ms":  ${MONOLITH_P99},
        "p999Ms": ${MONOLITH_P999}, "maxMs": ${MONOLITH_MAX}, "throughputRps": ${MONOLITH_RPS}
    },
    "vertx_risk_platform": {
        "p50Ms":  ${VRP_P50}, "p95Ms":  ${VRP_P95}, "p99Ms":  ${VRP_P99},
        "p999Ms": ${VRP_P999}, "maxMs": ${VRP_MAX}, "throughputRps": ${VRP_RPS}
    },
    "vertx_distributed": {
        "p50Ms":  ${VERTX_P50}, "p95Ms":  ${VERTX_P95}, "p99Ms":  ${VERTX_P99},
        "p999Ms": ${VERTX_P999}, "maxMs": ${VERTX_MAX}, "throughputRps": ${VERTX_RPS}
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
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "Metric" "bare-javac" "java-monolith" "vrp (HTTP+tokens)" "vertx-distributed"
  echo "------------------------------------------------------------------------"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p50"        "${B_P50_F}"  "${M_P50_F}"  "${P_P50_F}"  "${V_P50_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p95"        "${B_P95_F}"  "${M_P95_F}"  "${P_P95_F}"  "${V_P95_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p99"        "${B_P99_F}"  "${M_P99_F}"  "${P_P99_F}"  "${V_P99_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "p99.9"      "${B_P999_F}" "${M_P999_F}" "${P_P999_F}" "${V_P999_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "max"        "${B_MAX_F}"  "${M_MAX_F}"  "${P_MAX_F}"  "${V_MAX_F}"
  printf "%-10s  %-14s  %-14s  %-18s  %-18s\n" "throughput" "${B_RPS_F}"  "${M_RPS_F}"  "${P_RPS_F}"  "${V_RPS_F}"
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
    bare_vals  = [${BARE_P50},  ${BARE_P95},  ${BARE_P99},  ${BARE_P999}]
    vertx_vals = [${VERTX_P50}, ${VERTX_P95}, ${VERTX_P99}, ${VERTX_P999}]

    # Only plot if we have real values
    if any(v < 0 for v in bare_vals + vertx_vals):
        sys.exit(0)

    x = np.arange(len(metrics))
    width = 0.35

    fig, ax = plt.subplots(figsize=(9, 5))
    bars1 = ax.bar(x - width/2, bare_vals,  width, label='Bare-javac (HTTP)', color='#2196F3')
    bars2 = ax.bar(x + width/2, vertx_vals, width, label='Vert.x distributed', color='#FF5722')

    ax.set_ylabel('Latency (ms)')
    ax.set_title('Performance Competition: Bare-javac vs Vert.x distributed\n(${REQUESTS} requests, concurrency=${CONCURRENCY})')
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
set title 'Performance Competition: Bare-javac vs Vertx distributed'
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set boxwidth 0.9
set xtics ('p50' 0, 'p95' 1, 'p99' 2, 'p999' 3)
set ylabel 'Latency (ms)'
set logscale y
plot '-' using 2:xtic(1) title 'bare-javac', '' using 2:xtic(1) title 'vertx'
p50 ${BARE_P50}
p95 ${BARE_P95}
p99 ${BARE_P99}
p999 ${BARE_P999}
e
p50 ${VERTX_P50}
p95 ${VERTX_P95}
p99 ${VERTX_P99}
p999 ${VERTX_P999}
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
