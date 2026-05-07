#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-platform-stop"

for pod in controller usecase repository; do
  pidfile="$ROOT/.run/$pod.pid"
  if [[ -f "$pidfile" ]]; then
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid"
      echo "stopped $pod pid=$pid" | tee -a "$OUT_DIR/stdout.log"
    fi
    rm -f "$pidfile"
  fi
done

finalize_output 0
