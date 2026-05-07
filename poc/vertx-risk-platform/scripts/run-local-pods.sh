#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-platform-run"

cd "$ROOT"
echo "Building..." | tee "$OUT_DIR/stdout.log"
(cd "$REPO_ROOT" && ./gradlew :poc:vertx-risk-platform:shadowJar) >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"

mkdir -p "$ROOT/.run"
JAR="$ROOT/build/libs/vertx-risk-platform-0.1.0-SNAPSHOT.jar"
JAVA="${JAVA:-/opt/homebrew/opt/openjdk/bin/java}"
if [[ ! -x "$JAVA" ]]; then JAVA="java"; fi
if [[ ! -f "$JAR" ]]; then
  echo "missing jar: $JAR" | tee -a "$OUT_DIR/stderr.log"
  finalize_output 1
  exit 1
fi

for pod in repository usecase controller; do
  if [[ -f "$ROOT/.run/$pod.pid" ]] && kill -0 "$(cat "$ROOT/.run/$pod.pid")" 2>/dev/null; then
    echo "$pod already running pid=$(cat "$ROOT/.run/$pod.pid")" | tee -a "$OUT_DIR/stdout.log"
    continue
  fi
  "$JAVA" -jar "$JAR" "$pod" \
    > "$OUT_DIR/${pod}.log" 2>&1 &
  echo $! > "$ROOT/.run/$pod.pid"
  echo "started $pod pid=$(cat "$ROOT/.run/$pod.pid") log=$OUT_DIR/${pod}.log" | tee -a "$OUT_DIR/stdout.log"
  sleep 1
done

finalize_output 0
