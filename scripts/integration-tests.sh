#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "integration-tests"

if ! docker info > /dev/null 2>&1; then
  echo "ERROR: Docker not running. Start Docker/OrbStack/Colima first." | tee "$OUT_DIR/stderr.log"
  finalize_output 2
  exit 2
fi

echo "Running integration tests..." | tee "$OUT_DIR/stdout.log"

set +e
(cd "${REPO_ROOT}" && ./gradlew :tests:integration:test) \
  >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"
EXIT_CODE=$?
set -e

{
  echo "# Integration Tests"
  echo ""
  echo "**Exit code**: $EXIT_CODE"
  echo ""
  echo '```'
  tail -40 "$OUT_DIR/stdout.log"
  echo '```'
} > "$OUT_DIR/summary.md"

cp "$OUT_DIR/stdout.log" "$OUT_DIR/summary.txt"

finalize_output "$EXIT_CODE"
exit "$EXIT_CODE"
