#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

"$SCRIPT_DIR/build.sh"
cd "$POC_DIR"
docker compose up -d --build --remove-orphans

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  if curl -fsS --max-time 2 http://localhost:8090/healthz >/dev/null; then
    echo "vertx-service-mesh-bounded-contexts ready: http://localhost:8090"
    exit 0
  fi
  if docker compose ps --status restarting --services | grep -q .; then
    echo "ERROR: container restart loop detected" >&2
    docker compose ps >&2
    docker compose logs --tail=80 >&2
    exit 1
  fi
  sleep 1
done

echo "ERROR: vertx-service-mesh-bounded-contexts did not become ready within ${TIMEOUT_SECONDS}s" >&2
docker compose ps >&2
docker compose logs --tail=120 >&2
exit 1
