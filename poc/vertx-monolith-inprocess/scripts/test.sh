#!/usr/bin/env bash
# Run vertx-monolith-inprocess unit + integration tests.
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:vertx-monolith-inprocess:test "$@"
