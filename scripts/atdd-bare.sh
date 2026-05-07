#!/usr/bin/env bash
# Run the ATDD module (Cucumber-JVM) for the bare-javac risk engine PoC.
# Usage: ./scripts/atdd-bare.sh
# Filter by tag: cucumber_filter_tags=@idempotency ./scripts/atdd-bare.sh
#
# NOTE: Requires the risk-engine server to be running first:
#   ./gradlew :poc:java-risk-engine:run &
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

EXTRA_OPTS=""
if [[ -n "${cucumber_filter_tags:-}" ]]; then
  EXTRA_OPTS="-Dcucumber.filter.tags=${cucumber_filter_tags}"
fi

echo "==> Running ATDD tests for risk-engine (Cucumber-JVM)"
exec "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" :tests:risk-engine-atdd:test \
  -Patdd ${EXTRA_OPTS:+--tests "$EXTRA_OPTS"} "$@"
