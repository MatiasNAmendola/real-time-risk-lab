#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-up"

{
  echo "==> [up] Starting stack..."
  REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    up -d
} >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"

echo "==> [up] Waiting for controller-app healthcheck..."
ATTEMPTS=0
MAX=90
until REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    exec -T controller-app \
    wget -qO- http://localhost:8080/health > /dev/null 2>&1; do
  ATTEMPTS=$((ATTEMPTS + 1))
  if [[ $ATTEMPTS -ge $MAX ]]; then
    echo "ERROR: controller-app did not become healthy in time." | tee -a "$OUT_DIR/stderr.log"
    REPO_ROOT="$REPO_ROOT" docker compose \
      -f "$REPO_ROOT/compose/docker-compose.yml" \
      -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
      logs controller-app 2>&1 | tail -30 \
      | tee -a "$OUT_DIR/stderr.log"
    finalize_output 1
    exit 1
  fi
  echo "    ($ATTEMPTS/$MAX) waiting health"
  # Every 10 attempts (~50s) print docker compose ps for visibility
  if (( ATTEMPTS % 10 == 0 )); then
    echo "    --- docker compose ps (attempt $ATTEMPTS/$MAX) ---"
    REPO_ROOT="$REPO_ROOT" docker compose \
      -f "$REPO_ROOT/compose/docker-compose.yml" \
      -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
      ps 2>&1 | tail -20 || true
    echo "    ---"
  fi
  sleep 5
done

# Capture post-up cluster state
{
  echo ""
  echo "==> [up] docker compose ps"
  REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    ps
} | tee -a "$OUT_DIR/stdout.log" > "$OUT_DIR/services-status.txt"

{
  echo ""
  echo "==> [up] Stack is up."
  echo "    HTTP endpoint      : http://localhost:8080"
  echo "    Swagger UI         : http://localhost:8080/docs"
  echo "    OpenAPI JSON       : http://localhost:8080/openapi.json"
  echo "    AsyncAPI JSON      : http://localhost:8080/asyncapi.json"
  echo "    SSE stream         : curl -N http://localhost:8080/risk/stream"
  echo "    WebSocket          : wscat -c ws://localhost:8080/ws/risk"
  echo "    Redpanda Console   : http://localhost:9001 (add dev-tools override)"
  echo "    OpenObserve        : http://localhost:5080  (admin@example.com / Complexpass#)"
  echo ""
  echo "    Run ./scripts/demo.sh to execute all 6 demo flows."
} | tee -a "$OUT_DIR/stdout.log"

finalize_output 0
