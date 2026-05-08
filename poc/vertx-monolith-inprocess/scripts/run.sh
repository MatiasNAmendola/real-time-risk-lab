#!/usr/bin/env bash
# Run vertx-monolith-inprocess standalone against already-running infra.
# Requires: Postgres, Valkey, Redpanda, MinIO, ElasticMQ accessible at localhost.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
JAR="$REPO_ROOT/poc/vertx-monolith-inprocess/build/libs/vertx-monolith-inprocess.jar"

if [ ! -f "$JAR" ]; then
  echo "[run.sh] Fat jar not found — building first..."
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:vertx-monolith-inprocess:shadowJar
fi

exec java \
  -Xms128m -Xmx768m \
  -DRULES_CONFIG_PATH="${RULES_CONFIG_PATH:-$REPO_ROOT/examples/rules-config/v1/rules.yaml}" \
  -DADMIN_TOKEN="${ADMIN_TOKEN:-change-me-admin-token}" \
  -jar "$JAR" "$@"
