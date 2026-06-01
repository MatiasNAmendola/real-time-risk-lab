#!/usr/bin/env bash
# .ai/adapters/claude-code/install.sh
# Installs the Claude Code adapter: creates sub-agents under .claude/agents/
# Idempotent: safe to run multiple times.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
AI_DIR="$REPO_ROOT/.ai"
AGENTS_DIR="$REPO_ROOT/.claude/agents"

echo "=== Claude Code adapter install ==="
echo "Repo: $REPO_ROOT"

# Create the agents directory
mkdir -p "$AGENTS_DIR"

# Generate one sub-agent per skill
for skill_file in "$AI_DIR/primitives/skills/"*.md; do
    skill_name=$(basename "$skill_file" .md)
    agent_file="$AGENTS_DIR/${skill_name}.md"

    if [ -f "$agent_file" ]; then
        echo "  skip (exists): $agent_file"
        continue
    fi

    # Extract intent from frontmatter
    intent=$(grep "^intent:" "$skill_file" 2>/dev/null | head -1 | sed 's/^intent: //' | tr -d '"' || echo "Execute skill $skill_name")

    cat > "$agent_file" <<EOF
---
name: ${skill_name}
description: ${intent}
---

# Sub-agent: ${skill_name}

Load and execute the skill at: \`.ai/primitives/skills/${skill_name}.md\`

Follow every step in that skill file exactly. Apply all related rules listed in its frontmatter.

After completing the skill, save any decisions or discoveries to Engram with project: 'riskplatform/real-time-risk-lab'.
EOF
    echo "  created: $agent_file"
done

# Create settings.json only if it does not already exist
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
