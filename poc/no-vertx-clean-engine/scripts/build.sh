#!/usr/bin/env bash
# Build the risk-engine module (compiles + packages the distribution tar).
# Usage: ./scripts/build.sh
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:no-vertx-clean-engine:build "$@"
