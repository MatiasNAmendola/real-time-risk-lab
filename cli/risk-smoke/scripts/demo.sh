#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "risk-smoke-demo"

echo "==> Building risk-smoke..." | tee "$OUT_DIR/stdout.log"
cd "$ROOT"
make build >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"

echo "" | tee -a "$OUT_DIR/stdout.log"
echo "==> Running in headless mode (demo)..." | tee -a "$OUT_DIR/stdout.log"

set +e
./bin/risk-smoke --headless >> "$OUT_DIR/stdout.log" 2>> "$OUT_DIR/stderr.log" || true
EXIT_CODE=$?
set -e

{
  echo "# risk-smoke demo"
  echo ""
  echo "**Exit code**: $EXIT_CODE"
  echo ""
  echo '```'
  cat "$OUT_DIR/stdout.log"
  echo '```'
} > "$OUT_DIR/summary.md"

cp "$OUT_DIR/stdout.log" "$OUT_DIR/summary.txt"

echo "" | tee -a "$OUT_DIR/stdout.log"
echo "==> To launch the interactive TUI, run:" | tee -a "$OUT_DIR/stdout.log"
echo "    ./bin/risk-smoke" | tee -a "$OUT_DIR/stdout.log"

finalize_output "$EXIT_CODE"
exit "$EXIT_CODE"
