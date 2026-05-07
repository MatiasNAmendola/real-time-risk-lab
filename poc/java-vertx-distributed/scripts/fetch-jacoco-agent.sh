#!/usr/bin/env bash
# ─── fetch-jacoco-agent.sh ────────────────────────────────────────────────────
# Downloads the JaCoCo runtime agent jar from Maven Central.
# Idempotent: skips download if the jar already exists.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$ROOT/jacoco-agent"

if [[ -f "$ROOT/jacoco-agent/jacocoagent.jar" ]]; then
  echo "JaCoCo agent already present."
  exit 0
fi

curl -sSL -o "$ROOT/jacoco-agent/jacocoagent.jar" \
  https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar

echo "JaCoCo agent fetched."
