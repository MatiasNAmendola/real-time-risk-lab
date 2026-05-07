#!/usr/bin/env bash
# Run unit tests for the risk-engine module.
# Usage: ./scripts/test.sh
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:java-risk-engine:test "$@"
