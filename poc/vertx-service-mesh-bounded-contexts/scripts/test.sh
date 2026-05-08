#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew \
  :poc:vertx-service-mesh-bounded-contexts:shared:test \
  :poc:vertx-service-mesh-bounded-contexts:risk-decision-service:test \
  :poc:vertx-service-mesh-bounded-contexts:fraud-rules-service:test \
  :poc:vertx-service-mesh-bounded-contexts:ml-scorer-service:test \
  :poc:vertx-service-mesh-bounded-contexts:audit-service:test
