#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

source ../scripts/lib/output.sh 2>/dev/null || { echo "(output.sh not available, running plain)"; }

if command -v init_output >/dev/null 2>&1; then
  init_output "dashboard"
fi

docker compose up -d
echo
echo "Dashboard up at: http://localhost:8888"
echo
echo "Stop with: docker compose -f $(pwd)/docker-compose.yml down"

if command -v finalize_output >/dev/null 2>&1; then
  finalize_output 0
fi
