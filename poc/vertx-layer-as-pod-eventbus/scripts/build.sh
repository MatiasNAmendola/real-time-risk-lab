#!/usr/bin/env bash
# Build all Vert.x distributed app fat-jars via Gradle Shadow, then build Docker images.
# Usage: ./scripts/build.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
OTEL_DIR="$ROOT/otel"
AGENT_JAR="$OTEL_DIR/opentelemetry-javaagent.jar"

# ── 1. Download OTel Java agent if not present ────────────────────────────────
mkdir -p "$OTEL_DIR"
if [[ ! -f "$AGENT_JAR" ]]; then
  echo "==> [build] Downloading OpenTelemetry Java agent..."
  curl -sSL \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar" \
    -o "$AGENT_JAR"
  echo "==> [build] Agent downloaded: $(du -sh "$AGENT_JAR" | cut -f1)"
else
  echo "==> [build] OTel agent already present ($(du -sh "$AGENT_JAR" | cut -f1))"
fi

# ── 2. Gradle shadowJar ───────────────────────────────────────────────────────
echo "==> [build] Building fat-jars with Gradle Shadow..."
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" \
  :poc:vertx-layer-as-pod-eventbus:controller-app:shadowJar \
  :poc:vertx-layer-as-pod-eventbus:usecase-app:shadowJar \
  :poc:vertx-layer-as-pod-eventbus:repository-app:shadowJar \
  :poc:vertx-layer-as-pod-eventbus:consumer-app:shadowJar

echo "==> [build] Fat jars built:"
for app in controller-app usecase-app repository-app consumer-app; do
  JAR="$ROOT/$app/build/libs/$app-0.1.0-SNAPSHOT-all.jar"
  [[ -f "$JAR" ]] && echo "    $JAR ($(du -sh "$JAR" | cut -f1))"
done

# ── 3. Docker build ───────────────────────────────────────────────────────────
echo "==> [build] Building Docker images..."
docker compose -f "$ROOT/docker-compose.yml" build

echo ""
echo "==> [build] Done. Run ./scripts/up.sh to start the stack."
