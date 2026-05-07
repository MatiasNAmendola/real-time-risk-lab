#!/usr/bin/env bash
# Run the JMH in-process benchmark against the bare-javac PoC.
# Uses Gradle to build and the JMH launcher to run.
#
# Usage:  ./scripts/run-inprocess.sh [extra JMH flags]
# Example: ./scripts/run-inprocess.sh -wi 1 -i 3   (faster, for CI smoke)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$BENCH_DIR/.." && pwd)"

# shadowJar config sets archiveClassifier="" so the fat jar is named
# inprocess-bench-<version>.jar (no -all suffix). Resolve dynamically to
# survive version bumps and naming conventions.
LIBS_DIR="$BENCH_DIR/inprocess-bench/build/libs"
_resolve_jar() {
  ls "$LIBS_DIR"/inprocess-bench-*.jar 2>/dev/null \
    | grep -v -- '-sources\.jar$' \
    | head -n 1
}
JAR="$(_resolve_jar)"

if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
  echo "Fat JAR not found. Building..."
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :bench:inprocess-bench:shadowJar
  JAR="$(_resolve_jar)"
  if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
    echo "ERROR: shadowJar finished but no fat jar found in $LIBS_DIR" >&2
    exit 1
  fi
fi

echo "=== In-process JMH benchmark ==="
OUT_DIR="${OUT_DIR:-$BENCH_DIR/build/bench-inprocess}"
mkdir -p "$OUT_DIR"

java \
  --enable-preview \
  -jar "$JAR" \
  -rf json \
  -rff "$OUT_DIR/results.json" \
  "$@" | tee "$OUT_DIR/stdout.log"
