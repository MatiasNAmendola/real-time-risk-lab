#!/usr/bin/env bash
# Run java-monolith unit + integration tests.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:java-monolith:test "$@"
