#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-platform-smoke"
CONTROLLER_PORT="${CONTROLLER_PORT:-18080}"
USECASE_PORT="${USECASE_PORT:-18081}"
REPOSITORY_PORT="${REPOSITORY_PORT:-18082}"

run_check() {
  local label="$1"; shift
  local output
  set +e
  output=$(curl -fsS "$@" 2>&1)
  local rc=$?
  set -e
  if [[ $rc -eq 0 ]]; then
    echo "PASS: $label" | tee -a "$OUT_DIR/stdout.log"
    echo "$output" >> "$OUT_DIR/stdout.log"
  else
    echo "FAIL: $label" | tee -a "$OUT_DIR/stderr.log"
    echo "$output" >> "$OUT_DIR/stderr.log"
    SMOKE_FAIL=1
  fi
}

SMOKE_FAIL=0

printf '\n-- health --\n' | tee -a "$OUT_DIR/stdout.log"
run_check "controller health" http://localhost:${CONTROLLER_PORT}/health
run_check "usecase health"    http://localhost:${USECASE_PORT}/health
run_check "repository health" http://localhost:${REPOSITORY_PORT}/health

printf '\n-- evaluate --\n' | tee -a "$OUT_DIR/stdout.log"
run_check "evaluate tx-001" \
  -X POST http://localhost:${CONTROLLER_PORT}/risk/evaluate \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-vertx-001","customerId":"user-123","amountInCents":70000,"newDevice":false,"correlationId":"corr-vertx-001","idempotencyKey":"idem-vertx-001"}'

printf '\n-- idempotent retry --\n' | tee -a "$OUT_DIR/stdout.log"
run_check "idempotent retry" \
  -X POST http://localhost:${CONTROLLER_PORT}/risk/evaluate \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-vertx-001","customerId":"user-123","amountInCents":70000,"newDevice":false,"correlationId":"corr-vertx-retry","idempotencyKey":"idem-vertx-001"}'

printf '\n-- controller cannot access repository --\n' | tee -a "$OUT_DIR/stdout.log"
curl -sS http://localhost:${CONTROLLER_PORT}/debug/try-repository 2>&1 | head -20 | tee -a "$OUT_DIR/stdout.log"

{
  echo "# Vertx Risk Platform Smoke"
  echo ""
  if [[ $SMOKE_FAIL -eq 0 ]]; then
    echo "**Status**: ALL PASS"
  else
    echo "**Status**: SOME FAILURES — see stderr.log"
  fi
  echo ""
  echo '```'
  cat "$OUT_DIR/stdout.log"
  echo '```'
} > "$OUT_DIR/summary.md"

cp "$OUT_DIR/stdout.log" "$OUT_DIR/summary.txt"

finalize_output "$SMOKE_FAIL"
exit "$SMOKE_FAIL"
