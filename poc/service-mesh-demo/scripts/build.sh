#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew \
  :poc:service-mesh-demo:shared:build \
  :poc:service-mesh-demo:risk-decision-service:shadowJar \
  :poc:service-mesh-demo:fraud-rules-service:shadowJar \
  :poc:service-mesh-demo:ml-scorer-service:shadowJar \
  :poc:service-mesh-demo:audit-service:shadowJar
