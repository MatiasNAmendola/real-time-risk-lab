#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-platform-run"

cd "$ROOT"
echo "Building..." | tee "$OUT_DIR/stdout.log"
(cd "$REPO_ROOT" && ./gradlew :poc:vertx-layer-as-pod-http:shadowJar) >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"

mkdir -p "$ROOT/.run"
JAR="$ROOT/build/libs/vertx-layer-as-pod-http.jar"
JAVA="${JAVA:-/opt/homebrew/opt/openjdk/bin/java}"
CONTROLLER_PORT="${CONTROLLER_PORT:-18080}"
USECASE_PORT="${USECASE_PORT:-18081}"
REPOSITORY_PORT="${REPOSITORY_PORT:-18082}"
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
  CONTROLLER_PORT="$CONTROLLER_PORT" USECASE_PORT="$USECASE_PORT" REPOSITORY_PORT="$REPOSITORY_PORT" \
    "$JAVA" -jar "$JAR" "$pod" \
    > "$OUT_DIR/${pod}.log" 2>&1 &
  echo $! > "$ROOT/.run/$pod.pid"
  echo "started $pod pid=$(cat "$ROOT/.run/$pod.pid") log=$OUT_DIR/${pod}.log" | tee -a "$OUT_DIR/stdout.log"
  sleep 1
done

wait_for_health() {
  local name="$1"
  local url="$2"
  for _ in {1..60}; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name healthy" | tee -a "$OUT_DIR/stdout.log"
      return 0
    fi
    sleep 1
  done
  echo "$name did not become healthy: $url" | tee -a "$OUT_DIR/stderr.log"
  return 1
}

wait_for_health repository http://localhost:${REPOSITORY_PORT}/health
wait_for_health usecase http://localhost:${USECASE_PORT}/health
wait_for_health controller http://localhost:${CONTROLLER_PORT}/health

finalize_output 0
