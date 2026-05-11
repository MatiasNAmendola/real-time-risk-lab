#!/usr/bin/env bash
# Bring up the kafka-s3-tansu PoC: Floci (shared infra) + Tansu broker.
#
# This stack reuses the shared compose/docker-compose.yml for Floci (and any
# other infra you happen to bring along) and layers in the Tansu service via
# poc/kafka-s3-tansu/compose.override.yml.
#
# Usage:
#   ./scripts/up.sh           # bring up tansu + floci + floci-init
#   TIMEOUT_SECONDS=180 ./scripts/up.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$POC_DIR/../.." && pwd)"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

BASE="$REPO_ROOT/compose/docker-compose.yml"
OVERRIDE="$POC_DIR/compose.override.yml"

cd "$REPO_ROOT"
echo "[up] starting floci + floci-init + tansu ..."
docker compose -f "$BASE" -f "$OVERRIDE" up -d --wait floci floci-init tansu

echo "[up] tansu container state:"
docker compose -f "$BASE" -f "$OVERRIDE" ps tansu

# External wire-level smoke check.
# NOTE: edenhill/kcat:1.7.1 (modern librdkafka) FAILS the ApiVersionRequest
# against Tansu 0.6.0 with "Read underflow". Older confluentinc/cp-kafkacat:7.0.0
# handles the negotiation correctly. See README.md "What doesn't work".
echo "[up] probing tansu kafka wire on data-net (timeout ${TIMEOUT_SECONDS}s) ..."
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  if docker run --rm --network=compose_data-net confluentinc/cp-kafkacat:7.0.0 \
       kafkacat -L -b tansu:9092 >/dev/null 2>&1; then
    echo "[up] OK: tansu kafka handshake succeeded (cp-kafkacat 7.0.0)"
    exit 0
  fi
  sleep 1
done

echo "[up] ERROR: tansu did not accept kafka clients within ${TIMEOUT_SECONDS}s" >&2
docker compose -f "$BASE" -f "$OVERRIDE" logs --tail=120 tansu >&2 || true
exit 1
