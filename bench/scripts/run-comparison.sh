#!/usr/bin/env bash
# Full comparison run:
#   1. Build everything via Gradle
#   2. Run in-process JMH benchmark
#   3. Run distributed HTTP benchmark (requires Vert.x docker compose up)
#   4. Generate side-by-side comparison report
#
# Usage: ./scripts/run-comparison.sh [--skip-distributed]

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$BENCH_DIR/.." && pwd)"

SKIP_DIST=false
for arg in "$@"; do
  [[ "$arg" == "--skip-distributed" ]] && SKIP_DIST=true
done

echo "========================================"
echo "NaranjaX Performance Comparison Runner"
echo "========================================"

# ── 1. Build ──────────────────────────────────────────────────────────────────
echo "[1/4] Building all bench modules via Gradle..."
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" \
  :bench:inprocess-bench:shadowJar \
  :bench:distributed-bench:shadowJar \
  :bench:runner:shadowJar

# ── 2. In-process benchmark ───────────────────────────────────────────────────
echo "[2/4] Running in-process (JMH) benchmark..."
set +e
"$SCRIPT_DIR/run-inprocess.sh"
INPROC_EXIT=$?
set -e

# ── 3. Distributed benchmark ──────────────────────────────────────────────────
DIST_EXIT=0
if [[ "$SKIP_DIST" == "true" ]]; then
  echo "[3/4] Skipping distributed benchmark (--skip-distributed)"
else
  echo "[3/4] Running distributed HTTP benchmark..."
  set +e
  "$SCRIPT_DIR/run-distributed.sh"
  DIST_EXIT=$?
  set -e
  if [[ $DIST_EXIT -ne 0 ]]; then
    echo "      Distributed benchmark failed (exit $DIST_EXIT). Continuing."
    SKIP_DIST=true
  fi
fi

# ── 4. Comparison report ──────────────────────────────────────────────────────
echo "[4/4] Generating comparison report..."
RUNNER_JAR="$BENCH_DIR/runner/build/libs/runner-0.1.0-SNAPSHOT-all.jar"
INP_OUT="$BENCH_DIR/build/bench-inprocess"
DIST_OUT="$BENCH_DIR/build/bench-distributed"
CMP_OUT="$BENCH_DIR/build/bench-comparison"
mkdir -p "$CMP_OUT"

set +e
java -jar "$RUNNER_JAR" "$INP_OUT" "$DIST_OUT" "$CMP_OUT"
RUNNER_EXIT=$?
set -e

OVERALL_EXIT=0
[[ $INPROC_EXIT -ne 0 ]] && OVERALL_EXIT=$INPROC_EXIT
[[ $DIST_EXIT -ne 0 && "$SKIP_DIST" == "false" ]] && OVERALL_EXIT=$DIST_EXIT
[[ $RUNNER_EXIT -ne 0 ]] && OVERALL_EXIT=$RUNNER_EXIT

echo "========================================"
echo "Done. Reports: $CMP_OUT/"
exit "$OVERALL_EXIT"
