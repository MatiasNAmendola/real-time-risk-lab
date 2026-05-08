#!/usr/bin/env bash
# Run the distributed HTTP load generator against the Vert.x controller-app.
# Assumes docker compose is already up: cd poc/vertx-layer-as-pod-eventbus && docker compose up -d
#
# Usage:
#   ./scripts/run-distributed.sh [N] [M] [baseUrl]
#
#   N       = total requests      (default 5000)
#   M       = concurrency         (default 32)
#   baseUrl = controller-app URL  (default http://localhost:8080)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$BENCH_DIR/.." && pwd)"

N="${1:-5000}"
M="${2:-32}"
BASE_URL="${3:-http://localhost:8080}"

# shadowJar config sets archiveClassifier="" so the fat jar is named
# distributed-bench-<version>.jar (no -all suffix). Resolve dynamically to
# survive version bumps and naming conventions.
LIBS_DIR="$BENCH_DIR/distributed-bench/build/libs"
_resolve_jar() {
  ls "$LIBS_DIR"/distributed-bench-*.jar 2>/dev/null \
    | grep -v -- '-sources\.jar$' \
    | head -n 1
}
JAR="$(_resolve_jar)"

if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
  echo "Fat JAR not found. Building..."
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :bench:distributed-bench:shadowJar
  JAR="$(_resolve_jar)"
  if [[ -z "${JAR:-}" || ! -f "$JAR" ]]; then
    echo "ERROR: shadowJar finished but no fat jar found in $LIBS_DIR" >&2
    exit 1
  fi
fi

OUT_DIR="${OUT_DIR:-$BENCH_DIR/build/bench-distributed}"
mkdir -p "$OUT_DIR"

echo "=== Distributed HTTP benchmark ==="
echo "target=$BASE_URL  requests=$N  concurrency=$M"

java -jar "$JAR" "$N" "$M" "$BASE_URL" "$OUT_DIR" | tee "$OUT_DIR/stdout.log"
