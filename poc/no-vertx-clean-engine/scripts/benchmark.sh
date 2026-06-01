#!/usr/bin/env bash
# Run the in-process JMH benchmark for the risk engine.
# Usage: ./scripts/benchmark.sh
# JMH args can be passed via --args="<jmh flags>" when invoking via Gradle run,
# or run the dedicated bench module directly:
#   ./gradlew :bench:inprocess-bench:jmh
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :bench:inprocess-bench:jmh "$@"
