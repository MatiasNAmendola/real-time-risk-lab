#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew \
  :poc:vertx-service-mesh-bounded-contexts:shared:build \
  :poc:vertx-service-mesh-bounded-contexts:risk-decision-service:shadowJar \
  :poc:vertx-service-mesh-bounded-contexts:fraud-rules-service:shadowJar \
  :poc:vertx-service-mesh-bounded-contexts:ml-scorer-service:shadowJar \
  :poc:vertx-service-mesh-bounded-contexts:audit-service:shadowJar
