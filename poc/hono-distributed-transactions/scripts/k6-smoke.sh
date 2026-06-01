#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v k6 >/dev/null 2>&1; then
  echo "SKIP: k6 no está instalado. Instalalo con './nx setup --verify' o seguí docs/QUICK-REFERENCE.md#k6-grafana-load-testing."
  if [ "${K6_REQUIRED:-false}" = "true" ]; then
    exit 127
  fi
  exit 0
fi

PORT="${TEST_PORT:-43103}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${PORT}}"
LOG_FILE="${TMPDIR:-/tmp}/$(basename "$PWD")-k6-smoke.log"

if [ -z "${BASE_URL_ALREADY_RUNNING:-}" ]; then
  PORT="$PORT" TIGERBEETLE_ENABLED=false EDA_WORKER_ENABLED=false OTEL_SDK_DISABLED=true bun run src/main.ts >"$LOG_FILE" 2>&1 &
  APP_PID=$!
  cleanup() {
    kill "$APP_PID" >/dev/null 2>&1 || true
  }
  trap cleanup EXIT

  for _ in $(seq 1 75); do
    if curl -fsS "$BASE_URL/healthz" >/dev/null 2>&1; then
      break
    fi
    sleep 0.2
  done
  curl -fsS "$BASE_URL/healthz" >/dev/null || {
    echo "Servicio no levantó en $BASE_URL. Log:" >&2
    cat "$LOG_FILE" >&2 || true
    exit 1
  }
fi

BASE_URL="$BASE_URL" k6 run \
  -e BASE_URL="$BASE_URL" \
  -e K6_VUS="${K6_VUS:-2}" \
  -e K6_DURATION="${K6_DURATION:-10s}" \
  tests/k6/accounts-smoke.js
