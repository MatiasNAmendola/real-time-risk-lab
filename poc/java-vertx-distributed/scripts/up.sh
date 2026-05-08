#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"

source "$REPO_ROOT/scripts/lib/output.sh"
init_output "vertx-up"

# ---------------------------------------------------------------------------
# Build-freshness gate (root-cause fix for phase 9a/9i regress).
#
# Symptom: ./nx down vertx && ./nx up vertx leaves OLD images running because
# `docker compose up -d` does NOT rebuild on changed sources or jars.
# Result: hot-fixes baked into source disappear at every bring-up.
#
# Strategy:
#   1. Always run `./nx build vertx` (incremental — fast if jars are fresh).
#   2. Compare each fat-jar mtime against image creation time. If the jar is
#      newer (or image missing) → `docker compose build` for that service
#      BEFORE `up -d`.
#   3. Plain `up -d` afterwards (no `--build` to keep the no-op fast).
# ---------------------------------------------------------------------------
APPS=(controller-app usecase-app repository-app consumer-app)
COMPOSE_ARGS=(-f "$REPO_ROOT/compose/docker-compose.yml"
              -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml")

_newest_epoch() {
  local newest=0
  local f m
  for f in "$@"; do
    [[ -e "$f" ]] || continue
    m=$(stat -f '%m' "$f" 2>/dev/null || stat -c '%Y' "$f" 2>/dev/null || echo 0)
    [[ -n "$m" && "$m" -gt "$newest" ]] && newest="$m"
  done
  echo "$newest"
}

_container_created_epoch() {
  local service="$1"
  local cid created
  cid=$(REPO_ROOT="$REPO_ROOT" docker compose "${COMPOSE_ARGS[@]}" ps -q "$service" 2>/dev/null || true)
  [[ -n "$cid" ]] || { echo 0; return 0; }
  created=$(docker inspect "$cid" --format '{{.Created}}' 2>/dev/null || true)
  [[ -n "$created" ]] || { echo 0; return 0; }
  date -j -f "%Y-%m-%dT%H:%M:%S" "${created%.*}" +%s 2>/dev/null \
    || date -d "$created" +%s 2>/dev/null \
    || echo 0
}

echo "==> [up] Ensuring jars are fresh (incremental Gradle build)..."
"$REPO_ROOT/nx" build vertx >> "$OUT_DIR/stdout.log" 2>> "$OUT_DIR/stderr.log" || {
  echo "ERROR: gradle build failed; see $OUT_DIR/stderr.log" >&2
  finalize_output 1
  exit 1
}

echo "==> [up] Ensuring runtime agents are present..."
"$ROOT/scripts/fetch-jacoco-agent.sh" >> "$OUT_DIR/stdout.log" 2>> "$OUT_DIR/stderr.log" || {
  echo "ERROR: failed to fetch JaCoCo agent; see $OUT_DIR/stderr.log" >&2
  finalize_output 1
  exit 1
}

# Verify the produced jars are actual fat-jars (>1MB). Defends against the
# case where `:jar` ran but `:shadowJar` did not (archiveClassifier="" means
# they share a filename and a stale `:jar` artifact would be silently used).
echo "==> [up] Verifying fat-jars (must be >1MB, contain io.vertx classes)..."
for app in "${APPS[@]}"; do
  jar="$REPO_ROOT/poc/java-vertx-distributed/$app/build/libs/$app-0.1.0-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then
    echo "ERROR: missing jar $jar — gradle build did not produce shadow jar." >&2
    finalize_output 1
    exit 1
  fi
  size=$(stat -f '%z' "$jar" 2>/dev/null || stat -c '%s' "$jar")
  if (( size < 1048576 )); then
    echo "ERROR: $jar is only ${size} bytes — looks like a thin jar. Forcing :shadowJar." >&2
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" \
      ":poc:java-vertx-distributed:$app:shadowJar" --rerun-tasks \
      >> "$OUT_DIR/stdout.log" 2>> "$OUT_DIR/stderr.log" || {
      echo "ERROR: forced shadowJar for $app failed." >&2
      finalize_output 1
      exit 1
    }
    size=$(stat -f '%z' "$jar" 2>/dev/null || stat -c '%s' "$jar")
    if (( size < 1048576 )); then
      echo "ERROR: shadowJar still produced thin jar ($size bytes) for $app." >&2
      finalize_output 1
      exit 1
    fi
  fi
done

# Determine which services need a Docker rebuild: jar newer than image, or no image.
REBUILD_LIST=()
for app in "${APPS[@]}"; do
  jar="$REPO_ROOT/poc/java-vertx-distributed/$app/build/libs/$app-0.1.0-SNAPSHOT.jar"
  jar_mtime=$(stat -f '%m' "$jar" 2>/dev/null || stat -c '%Y' "$jar" 2>/dev/null || echo 0)
  img_created=$(docker inspect "riskplatform/$app:latest" --format '{{.Created}}' 2>/dev/null || true)
  if [[ -z "$img_created" ]]; then
    REBUILD_LIST+=("$app")
  else
    img_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${img_created%.*}" +%s 2>/dev/null \
                || date -d "$img_created" +%s 2>/dev/null || echo 0)
    if (( jar_mtime > img_epoch )); then
      REBUILD_LIST+=("$app")
    fi
  fi
done

if (( ${#REBUILD_LIST[@]} > 0 )); then
  echo "==> [up] Rebuilding Docker images (jars newer than images): ${REBUILD_LIST[*]}"
  REPO_ROOT="$REPO_ROOT" docker compose "${COMPOSE_ARGS[@]}" \
    build "${REBUILD_LIST[@]}" \
    >> "$OUT_DIR/stdout.log" 2>> "$OUT_DIR/stderr.log" || {
    echo "ERROR: docker compose build failed; see $OUT_DIR/stderr.log" >&2
    finalize_output 1
    exit 1
  }
else
  echo "==> [up] Docker images already current with jars."
fi

# Compose can leave an old container running when only override env/resources,
# mounted Hazelcast XML, or JVM flags changed. That is exactly how Phase 9a/9i
# appeared to regress: source and compose were fixed, but containers still ran
# stale env/images. Recreate only the Java app containers when their runtime
# inputs changed; keep infra volumes/processes untouched for a fast bring-up.
RUNTIME_INPUTS=(
  "$REPO_ROOT/compose/docker-compose.yml"
  "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml"
  "$REPO_ROOT/poc/java-vertx-distributed/hazelcast/hazelcast.xml"
)
RECREATE_LIST=()
for app in "${APPS[@]}"; do
  jar="$REPO_ROOT/poc/java-vertx-distributed/$app/build/libs/$app-0.1.0-SNAPSHOT.jar"
  newest_runtime=$(_newest_epoch "$jar" "${RUNTIME_INPUTS[@]}" \
    "$REPO_ROOT/poc/java-vertx-distributed/$app/Dockerfile")
  container_epoch=$(_container_created_epoch "$app")
  if (( container_epoch == 0 || newest_runtime > container_epoch )); then
    RECREATE_LIST+=("$app")
  fi
  # Rebuilt images must be picked up even if Docker preserves the same tag.
  for rebuilt in "${REBUILD_LIST[@]:-}"; do
    [[ "$rebuilt" == "$app" ]] && RECREATE_LIST+=("$app")
  done
done
if (( ${#RECREATE_LIST[@]} > 0 )); then
  DEDUPED_RECREATE_LIST=()
  for app in "${RECREATE_LIST[@]}"; do
    already=0
    for seen in "${DEDUPED_RECREATE_LIST[@]}"; do
      [[ "$seen" == "$app" ]] && already=1
    done
    (( already == 0 )) && DEDUPED_RECREATE_LIST+=("$app")
  done
  RECREATE_LIST=("${DEDUPED_RECREATE_LIST[@]}")
fi

mkdir -p "$OUT_DIR"
{
  echo "==> [up] Starting stack..."
  REPO_ROOT="$REPO_ROOT" docker compose "${COMPOSE_ARGS[@]}" up -d --remove-orphans
  if (( ${#RECREATE_LIST[@]} > 0 )); then
    echo "==> [up] Recreating app containers with changed runtime inputs: ${RECREATE_LIST[*]}"
    REPO_ROOT="$REPO_ROOT" docker compose "${COMPOSE_ARGS[@]}" \
      up -d --force-recreate --no-deps "${RECREATE_LIST[@]}"
  fi
} >> "$OUT_DIR/stdout.log" 2> "$OUT_DIR/stderr.log"

echo "==> [up] Waiting for controller-app readiness (/ready probes clustered EventBus)..."
ATTEMPTS=0
MAX=90
until REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    exec -T controller-app \
    wget -qO- http://localhost:8080/ready > /dev/null 2>&1; do
  ATTEMPTS=$((ATTEMPTS + 1))
  if [[ $ATTEMPTS -ge $MAX ]]; then
    echo "ERROR: controller-app did not become healthy in time." | tee -a "$OUT_DIR/stderr.log"
    REPO_ROOT="$REPO_ROOT" docker compose \
      -f "$REPO_ROOT/compose/docker-compose.yml" \
      -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
      logs controller-app 2>&1 | tail -30 \
      | tee -a "$OUT_DIR/stderr.log"
    finalize_output 1
    exit 1
  fi
    echo "    ($ATTEMPTS/$MAX) waiting readiness"
  # Every 10 attempts (~50s) print docker compose ps for visibility
  if (( ATTEMPTS % 10 == 0 )); then
    echo "    --- docker compose ps (attempt $ATTEMPTS/$MAX) ---"
    REPO_ROOT="$REPO_ROOT" docker compose \
      -f "$REPO_ROOT/compose/docker-compose.yml" \
      -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
      ps 2>&1 | tail -20 || true
    echo "    ---"
  fi
  sleep 5
done

# Capture post-up cluster state
{
  echo ""
  echo "==> [up] docker compose ps"
  REPO_ROOT="$REPO_ROOT" docker compose \
    -f "$REPO_ROOT/compose/docker-compose.yml" \
    -f "$REPO_ROOT/poc/java-vertx-distributed/compose.override.yml" \
    ps
} | tee -a "$OUT_DIR/stdout.log" > "$OUT_DIR/services-status.txt"

{
  echo ""
  echo "==> [up] Stack is up."
  echo "    HTTP endpoint      : http://localhost:8080"
  echo "    Swagger UI         : http://localhost:8080/docs"
  echo "    OpenAPI JSON       : http://localhost:8080/openapi.json"
  echo "    AsyncAPI JSON      : http://localhost:8080/asyncapi.json"
  echo "    SSE stream         : curl -N http://localhost:8080/risk/stream"
  echo "    WebSocket          : wscat -c ws://localhost:8080/ws/risk"
  echo "    Redpanda Console   : http://localhost:9001 (add dev-tools override)"
  echo "    OpenObserve        : http://localhost:5080  (admin@example.com / Complexpass#)"
  echo ""
  echo "    Run ./scripts/demo.sh to execute all 6 demo flows."
} | tee -a "$OUT_DIR/stdout.log"

finalize_output 0
