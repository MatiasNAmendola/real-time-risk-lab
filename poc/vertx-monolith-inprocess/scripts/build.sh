#!/usr/bin/env bash
# Build the vertx-monolith-inprocess fat jar
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:vertx-monolith-inprocess:shadowJar "$@"
