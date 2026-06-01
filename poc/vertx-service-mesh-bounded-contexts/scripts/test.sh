#!/usr/bin/env bash
REPO_ROOT_FOR_JAVA_ENV="$(cd "$(dirname "$0")/../../.." && pwd)"
source "$REPO_ROOT_FOR_JAVA_ENV/scripts/lib/java-env.sh"
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew \
  :poc:vertx-service-mesh-bounded-contexts:shared:test \
  :poc:vertx-service-mesh-bounded-contexts:risk-decision-service:test \
  :poc:vertx-service-mesh-bounded-contexts:fraud-rules-service:test \
  :poc:vertx-service-mesh-bounded-contexts:ml-scorer-service:test \
  :poc:vertx-service-mesh-bounded-contexts:audit-service:test
