#!/usr/bin/env bash
# Build the java-monolith fat jar
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :poc:java-monolith:shadowJar "$@"
