#!/usr/bin/env bash
# ─── atdd-coverage.sh ─────────────────────────────────────────────────────────
# Runs the ATDD suite and generates a cross-module JaCoCo coverage report.
#
# Coverage flow:
#   1. Karate tests run against the live docker-compose stack.
#   2. Gradle JaCoCo tasks collect execution data to each service
#      (ports 6301-6304) and write .exec files under build/jacoco/.
#   3. jacocoMergedReport generates HTML + XML under build/reports/jacoco/merged/
#      using Gradle production class files from sibling modules.
#
# Prerequisites:
#   - docker compose up (./scripts/up.sh)
#   - Services built with JaCoCo TCP agent (ENTRYPOINT includes -javaagent:/jacoco/jacocoagent.jar)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
GRADLE_CMD="$REPO_ROOT/gradlew"

# Ensure JaCoCo agent jar is present (for Docker image build)
echo "==> [atdd-coverage] Ensuring JaCoCo agent is present ..."
"$SCRIPT_DIR/fetch-jacoco-agent.sh"

CONTROLLER_URL="${CONTROLLER_URL:-http://localhost:8080}"
echo "==> [atdd-coverage] Checking service at $CONTROLLER_URL/health ..."
if ! curl -sf --max-time 3 "$CONTROLLER_URL/health" > /dev/null 2>&1; then
  echo ""
  echo "ERROR: expected service at $CONTROLLER_URL — run docker compose up first."
  echo "       Hint: ./scripts/up.sh"
  exit 1
fi

echo "==> [atdd-coverage] Running tests + collecting cross-module coverage ..."
cd "$REPO_ROOT"
# Gradle: run ATDD tests for the distributed stack; aggregated JaCoCo wiring lives in build.gradle.kts.
"$GRADLE_CMD" :poc:vertx-layer-as-pod-eventbus:atdd-tests:test \
  -Dkarate.env="${KARATE_ENV:-local}" "$@"

AGGREGATED_REPORT="$ROOT/atdd-tests/build/reports/jacoco/test/html/index.html"
if [[ -f "$AGGREGATED_REPORT" ]]; then
  echo "==> [atdd-coverage] Aggregated report generated: $AGGREGATED_REPORT"
  if command -v open &>/dev/null; then
    open "$AGGREGATED_REPORT"
  else
    echo "    Open manually: file://$AGGREGATED_REPORT"
  fi
else
  echo "WARN: aggregated report not found at $AGGREGATED_REPORT"
  echo "      If compose is down, dump will fail with 'connection refused' — this is expected."
  echo "      Per-module report (atdd-tests helpers only): $ROOT/atdd-tests/build/reports/jacoco/test/html/index.html"
fi
