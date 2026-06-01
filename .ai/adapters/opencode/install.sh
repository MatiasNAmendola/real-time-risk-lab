#!/usr/bin/env bash
# .ai/adapters/opencode/install.sh
# Installs the opencode adapter: generates opencode.json at the project root
# Idempotent.
# Confidence: medium — main config is documented; internal agents/skills subdirs are uncertain.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== opencode adapter install ==="

DEST="$REPO_ROOT/opencode.json"
if [ -f "$DEST" ]; then
    echo "  already exists: $DEST (skipping, run with --force to overwrite)"
    if [ "$1" != "--force" ]; then
        echo "opencode adapter already installed."
        exit 0
    fi
fi

cat > "$DEST" <<'EOF'
{
  "instructions": "Real-Time Risk Lab architecture exploration. Stack: Java 21 LTS executable baseline (Java 25 LTS documented target), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda. Java 21 executable baseline; Java 25 documented target. Clean Architecture: domain/ must NOT import from application/ or infrastructure/. ATDD first: write .feature before production code. Every request must produce trace + log + metric via OpenTelemetry. Check .ai/primitives/skills/ and .ai/primitives/rules/ before implementing. Editing project areas: edit poc/, tests/, cli/, docs/ and vault/ only when required; read applicable .ai/primitives first.",
  "provider": "anthropic",
  "model": "claude-sonnet-4-6",
  "mcp": {
    "servers": {}
  }
}
EOF

echo "  created: $DEST"

# Note: opencode global config is at ~/.config/opencode/opencode.json
# Project opencode.json has higher precedence than global config.
# Internal ~/.config/opencode/agents/ and ~/.config/opencode/skills/ structure
# is not fully specified in official docs as of 2026-05-07 (Medium confidence).

echo ""
echo "opencode adapter installed."
echo "Project config: $DEST"
echo "Note: ~/.config/opencode/opencode.json is the global config (lower precedence)."
echo "Note: agents/ and skills/ subdirs of ~/.config/opencode/ have undocumented internal structure."
