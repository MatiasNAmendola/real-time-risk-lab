#!/usr/bin/env bash
# .ai/adapters/opencode/install.sh
# Instala el adapter de opencode: genera opencode.json en root del proyecto
# Idempotente.
# Confianza: MEDIA — config principal bien documentada; estructura interna de agents/skills/ subdirs incierta.

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
  "instructions": "Risk Decision Platform architecture exploration. Stack: Java 25 LTS, Vert.x 5.0.12, Maven, Postgres 16, Valkey 8, Redpanda. Java 25 only — no downgrade. Clean Architecture: domain/ must NOT import from application/ or infrastructure/. ATDD first: write .feature before production code. Every request must produce trace + log + metric via OpenTelemetry. Check .ai/primitives/skills/ and .ai/primitives/rules/ before implementing. Do not touch: poc/, tests/, cli/, docs/, vault/.",
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
