#!/usr/bin/env bash
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

"$SCRIPT_DIR/build.sh"
cd "$POC_DIR"
docker compose up -d --build --remove-orphans

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  if curl -fsS --max-time 2 http://localhost:8090/healthz >/dev/null 2>&1; then
    readiness_payload='{"transactionId":"tx-service-mesh-readiness","customerId":"readiness","amountCents":1000,"newDevice":false}'
    if curl -fsS --max-time 5 \
      -H 'Content-Type: application/json' \
      -H 'X-Correlation-Id: readiness-service-mesh' \
      -d "$readiness_payload" \
      http://localhost:8090/risk >/dev/null 2>&1; then
      echo "vertx-service-mesh-bounded-contexts ready: http://localhost:8090"
      exit 0
    fi
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
