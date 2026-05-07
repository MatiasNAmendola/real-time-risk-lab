#!/usr/bin/env bash
# .ai/adapters/continue/install.sh
# Instala el adapter de Continue: genera .continuerc.json y .continue/prompts/
# Idempotente.
# Nota: el config global (~/.continue/config.yaml) lo gestiona el usuario, no este script.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Continue adapter install ==="

# .continuerc.json — override de proyecto (merge sobre config.yaml global)
DEST="$REPO_ROOT/.continuerc.json"
if [ -f "$DEST" ]; then
    echo "  already exists: $DEST (skipping)"
else
cat > "$DEST" <<'EOF'
{
  "mergeBehavior": "merge",
  "rules": [
    "Java 25 LTS only. Do NOT downgrade to 21. Use --release 25 in pom.xml.",
    "Clean Architecture: domain/ must NOT import from application/ or infrastructure/.",
    "ATDD first: write .feature file before any production code.",
    "Every request must produce trace + log + metric via OpenTelemetry. correlationId in MDC and header.",
    "Check .ai/primitives/skills/ and .ai/primitives/rules/ before implementing.",
    "Do not touch: poc/, tests/, cli/, docs/, vault/ — user ownership."
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

# .continue/prompts/ — reemplazo de slashCommands (nuevo formato post-migración)
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
