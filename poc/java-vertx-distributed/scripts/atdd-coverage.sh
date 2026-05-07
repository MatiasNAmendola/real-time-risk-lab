#!/usr/bin/env bash
# ─── atdd-coverage.sh ─────────────────────────────────────────────────────────
# Runs the ATDD suite and generates a cross-module JaCoCo coverage report.
#
# Coverage flow:
#   1. Karate tests run against the live docker-compose stack.
#   2. post-integration-test: jacoco:dump connects via TCP to each service
#      (ports 6301-6304) and writes jacoco-<service>.exec files.
#   3. jacoco:merge combines all .exec files into jacoco-merged.exec.
#   4. jacoco:report generates HTML + XML under target/site/jacoco-aggregated/
#      using production class files from the sibling modules.
#
# Prerequisites:
#   - docker compose up (./scripts/up.sh)
#   - Services built with JaCoCo TCP agent (ENTRYPOINT includes -javaagent:/jacoco/jacocoagent.jar)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA25="/opt/homebrew/opt/openjdk@25/bin/java"
if [[ -x "$JAVA25" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@25"
fi

MVN_CMD="mvn"
if command -v /opt/homebrew/bin/mvn &>/dev/null; then
  MVN_CMD="/opt/homebrew/bin/mvn"
fi

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
cd "$ROOT"
# verify phase: runs tests (surefire) + post-integration-test (dump/merge/report)
$MVN_CMD -pl atdd-tests verify -Dkarate.env="${KARATE_ENV:-local}" "$@"

AGGREGATED_REPORT="$ROOT/atdd-tests/target/site/jacoco-aggregated/index.html"
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
  echo "      Per-module report (atdd-tests helpers only): $ROOT/atdd-tests/target/site/jacoco/index.html"
fi
