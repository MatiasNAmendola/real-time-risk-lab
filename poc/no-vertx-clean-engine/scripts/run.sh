#!/usr/bin/env bash
# Build and run the CLI risk-engine entry point.
# Usage: ./scripts/run.sh [-- <app-args>]
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:no-vertx-clean-engine:run "$@"
