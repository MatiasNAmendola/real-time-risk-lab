#!/usr/bin/env bash
# .ai/adapters/claude-code/install.sh
# Instala el adapter de Claude Code: crea sub-agents en .claude/agents/
# Idempotente: se puede correr multiples veces.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
AI_DIR="$REPO_ROOT/.ai"
AGENTS_DIR="$REPO_ROOT/.claude/agents"

echo "=== Claude Code adapter install ==="
echo "Repo: $REPO_ROOT"

# Crear directorio de agents
mkdir -p "$AGENTS_DIR"

# Generar un sub-agent por skill
for skill_file in "$AI_DIR/primitives/skills/"*.md; do
    skill_name=$(basename "$skill_file" .md)
    agent_file="$AGENTS_DIR/${skill_name}.md"

    if [ -f "$agent_file" ]; then
        echo "  skip (exists): $agent_file"
        continue
    fi

    # Extraer intent del frontmatter
    intent=$(grep "^intent:" "$skill_file" 2>/dev/null | head -1 | sed 's/^intent: //' | tr -d '"' || echo "Execute skill $skill_name")

    cat > "$agent_file" <<EOF
---
name: ${skill_name}
description: ${intent}
---

# Sub-agent: ${skill_name}

Load and execute the skill at: \`.ai/primitives/skills/${skill_name}.md\`

Follow every step in that skill file exactly. Apply all related rules listed in its frontmatter.

After completing the skill, save any decisions or discoveries to Engram with project: 'naranjax/practica-entrevista'.
EOF
    echo "  created: $agent_file"
done

# Verificar que settings.json existe (no sobreescribir si ya existe)
SETTINGS="$REPO_ROOT/.claude/settings.json"
if [ ! -f "$SETTINGS" ]; then
    cat > "$SETTINGS" <<'EOF'
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "echo 'Pre-tool hook: secrets check would run here'"
          }
        ]
      }
    ]
  }
}
EOF
    echo "  created: $SETTINGS (minimal)"
else
    echo "  skip (exists): $SETTINGS"
fi

echo ""
echo "Claude Code adapter installed."
echo "Sub-agents created in: $AGENTS_DIR"
echo ""
echo "To use a skill, ask Claude: 'use the add-rest-endpoint skill'"
echo "or reference it directly: '@.ai/primitives/skills/add-rest-endpoint.md'"
