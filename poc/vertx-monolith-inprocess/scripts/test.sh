#!/usr/bin/env bash
# Run vertx-monolith-inprocess unit + integration tests.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:vertx-monolith-inprocess:test "$@"
