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
      for _ in {1..30}; do
        if ! kill -0 "$pid" 2>/dev/null; then
          break
        fi
        sleep 0.2
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
        echo "force-stopped $pod pid=$pid" | tee -a "$OUT_DIR/stdout.log"
      else
        echo "stopped $pod pid=$pid" | tee -a "$OUT_DIR/stdout.log"
      fi
    fi
    rm -f "$pidfile"
  fi
done

finalize_output 0
