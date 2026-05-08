#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew \
  :poc:service-mesh-demo:shared:test \
  :poc:service-mesh-demo:risk-decision-service:test \
  :poc:service-mesh-demo:fraud-rules-service:test \
  :poc:service-mesh-demo:ml-scorer-service:test \
  :poc:service-mesh-demo:audit-service:test
