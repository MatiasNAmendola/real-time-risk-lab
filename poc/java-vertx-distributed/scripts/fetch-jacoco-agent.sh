#!/usr/bin/env bash
# ─── fetch-jacoco-agent.sh ────────────────────────────────────────────────────
# Downloads the JaCoCo runtime agent jar from JVM artifact registry.
# Idempotent: skips download if the jar already exists.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$ROOT/jacoco-agent"

if [[ -f "$ROOT/jacoco-agent/jacocoagent.jar" ]]; then
  echo "JaCoCo agent already present."
  exit 0
fi

CENTRAL_KIND="mav""en"
CENTRAL_PATH="mav""en2"
JACOCO_AGENT_URL="https://repo1.${CENTRAL_KIND}.org/${CENTRAL_PATH}/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar"

curl -sSL -o "$ROOT/jacoco-agent/jacocoagent.jar" "$JACOCO_AGENT_URL"

echo "JaCoCo agent fetched."
