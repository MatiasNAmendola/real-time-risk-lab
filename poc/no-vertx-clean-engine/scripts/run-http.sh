#!/usr/bin/env bash
# Build and start the HTTP risk-engine server (blocks until SIGTERM).
# Usage: RISK_HTTP_PORT=8081 ./scripts/run-http.sh
#        ./nx run no-vertx-clean-engine --port 8081
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PORT="${RISK_HTTP_PORT:-8081}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      PORT="${2:?missing value for --port}"
      shift 2
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 2
      ;;
  esac
done


"$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:no-vertx-clean-engine:shadowJar >/dev/null
JAR="$REPO_ROOT/poc/no-vertx-clean-engine/build/libs/no-vertx-clean-engine.jar"
exec java -cp "$JAR" io.riskplatform.riskdecision.cleanengine.cmd.HttpRunner --port "$PORT"
