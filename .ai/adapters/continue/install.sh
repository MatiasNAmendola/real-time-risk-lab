#!/usr/bin/env bash
# .ai/adapters/continue/install.sh
# Installs the Continue adapter: generates .continuerc.json and .continue/prompts/
# Idempotent.
# Note: global config (~/.continue/config.yaml) is user-managed, not managed by this script.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Continue adapter install ==="

# .continuerc.json — project override merged over global config.yaml
DEST="$REPO_ROOT/.continuerc.json"
if [ -f "$DEST" ]; then
    echo "  already exists: $DEST (skipping)"
else
cat > "$DEST" <<'EOF'
{
  "mergeBehavior": "merge",
  "rules": [
    "Java 21 LTS executable baseline. Use --release 21; Java 25 LTS remains a documented target.",
    "Clean Architecture: domain/ must NOT import from application/ or infrastructure/.",
    "ATDD first: write .feature file before any production code.",
    "Every request must produce trace + log + metric via OpenTelemetry. correlationId in MDC and header.",
    "Check .ai/primitives/skills/ and .ai/primitives/rules/ before implementing.",
    "Editing project areas: edit poc/, tests/, cli/, docs/ and vault/ only when required; read applicable .ai/primitives first."
  ],
  "contextProviders": [
    { "name": "code" },
    { "name": "docs" },
    { "name": "diff" },
    { "name": "open" }
  ]
}
EOF
    echo "  created: $DEST"
fi

# .continue/prompts/ — replacement for deprecated slashCommands
mkdir -p "$REPO_ROOT/.continue/prompts"

ATDD_PROMPT="$REPO_ROOT/.continue/prompts/atdd-feature.prompt"
if [ ! -f "$ATDD_PROMPT" ]; then
cat > "$ATDD_PROMPT" <<'EOF'
name: atdd-feature
description: Generate a Karate/Cucumber .feature file following ATDD conventions
---
Generate a .feature file for the following scenario.

Follow .ai/primitives/rules/testing-atdd.md:
- Scenario title in plain English
- Given/When/Then format
- Include tags: @smoke for happy path, @regression for edge cases
- Use Background for shared setup

Feature: {{{ input }}}
EOF
    echo "  created: $ATDD_PROMPT"
fi

echo ""
echo "Continue adapter installed."
echo "  Project override: $DEST"
echo "  Prompt files:     $REPO_ROOT/.continue/prompts/"
echo ""
echo "NOTE: Continue uses ~/.continue/config.yaml (global) as main config."
echo "  - config.yaml is the canonical format (config.json is deprecated)."
echo "  - slashCommands in config.json is deprecated — use .continue/prompts/*.prompt instead."
echo "  - This adapter only manages the project override (.continuerc.json)."
echo "  - Install the Continue VS Code extension: continue.continue"
