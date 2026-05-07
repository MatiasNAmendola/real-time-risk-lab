#!/usr/bin/env bash
# Run the ATDD Karate suite against the running docker-compose stack.
# Prerequisites: docker compose up (./scripts/up.sh) must have been called first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Ensure JaCoCo agent jar is present ───────────────────────────────────────
echo "==> [atdd] Ensuring JaCoCo agent is present ..."
"$SCRIPT_DIR/fetch-jacoco-agent.sh"

# ── Smoke-check: is the controller reachable? ─────────────────────────────────
CONTROLLER_URL="${CONTROLLER_URL:-http://localhost:8080}"
echo "==> [atdd] Checking service at $CONTROLLER_URL/health ..."
if ! curl -sf --max-time 3 "$CONTROLLER_URL/health" > /dev/null 2>&1; then
  echo ""
  echo "ERROR: expected service at $CONTROLLER_URL — run docker compose up first."
  echo "       Hint: ./scripts/up.sh"
  exit 1
fi
echo "==> [atdd] Service is UP. Running Karate suite ..."

# ── Run tests via Gradle ──────────────────────────────────────────────────────
set +e
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" \
  :poc:java-vertx-distributed:atdd-tests:test \
  -Patdd -Pkarate.env="${KARATE_ENV:-local}" "$@"
EXIT_CODE=$?
set -e

exit "$EXIT_CODE"
