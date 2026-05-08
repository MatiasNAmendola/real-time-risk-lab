#!/usr/bin/env bash
# Build and run the CLI risk-engine entry point.
# Usage: ./scripts/run.sh [-- <app-args>]
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:no-vertx-clean-engine:run "$@"
