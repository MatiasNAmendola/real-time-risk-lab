#!/usr/bin/env bash
# Build and start the HTTP risk-engine server (blocks until SIGTERM).
# Usage: RISK_HTTP_PORT=8081 ./scripts/run-http.sh
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PORT="${RISK_HTTP_PORT:-8081}"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:java-risk-engine:run \
  --args="--port $PORT" "$@"
