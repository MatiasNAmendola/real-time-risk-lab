#!/usr/bin/env bash
# Run ATDD tests + generate JaCoCo coverage report, then open it in the browser.
# Usage: ./scripts/atdd-bare-coverage.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
ATDD_DIR="$PROJECT_ROOT/tests/risk-engine-atdd"
REPORT="$ATDD_DIR/target/site/jacoco/index.html"

echo "==> Running ATDD tests + JaCoCo report for risk-engine"
echo ""

cd "$ATDD_DIR"

set +e
mvn verify
MVN_EXIT=$?
set -e

echo ""
echo "==> Generating structured report..."
bash "$ATDD_DIR/scripts/report.sh" "$MVN_EXIT"

echo ""
echo "==> JaCoCo HTML: $REPORT"
if [[ -f "$REPORT" ]]; then
  if command -v open &>/dev/null; then
    open "$REPORT"          # macOS
  elif command -v xdg-open &>/dev/null; then
    xdg-open "$REPORT"      # Linux
  else
    echo "    Open manually: file://$REPORT"
  fi
else
  echo "    Report not found — check build output for errors."
fi

exit $MVN_EXIT
