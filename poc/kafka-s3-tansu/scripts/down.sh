#!/usr/bin/env bash
# Tear down the kafka-s3-tansu PoC stack (does NOT remove the shared floci
# infra brought up by other PoCs — only the services declared in this PoC's
# override file).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$POC_DIR/../.." && pwd)"

BASE="$REPO_ROOT/compose/docker-compose.yml"
OVERRIDE="$POC_DIR/compose.override.yml"

cd "$REPO_ROOT"
docker compose -f "$BASE" -f "$OVERRIDE" stop tansu || true
docker compose -f "$BASE" -f "$OVERRIDE" rm -f tansu || true
echo "[down] tansu stopped (floci/floci-init left running so other PoCs are unaffected)."
echo "[down] to fully tear down infra: docker compose -f $BASE down --remove-orphans"
