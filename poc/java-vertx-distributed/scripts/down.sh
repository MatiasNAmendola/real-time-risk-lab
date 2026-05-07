#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-down"

{
  echo "==> [down] Stopping stack..."
  REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    down 2>&1
  echo "==> [down] Done."
  echo "    To remove volumes too: docker compose -f $REPO_ROOT/compose/docker-compose.yml down -v"
} 2>&1 | tee "$OUT_DIR/stdout.log"

finalize_output 0
