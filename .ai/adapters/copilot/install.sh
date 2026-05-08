#!/usr/bin/env bash
# .ai/adapters/copilot/install.sh
# Instala el adapter de GitHub Copilot: genera .github/copilot-instructions.md
# Idempotente.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Copilot adapter install ==="
mkdir -p "$REPO_ROOT/.github"

DEST="$REPO_ROOT/.github/copilot-instructions.md"
if [ -f "$DEST" ]; then
    echo "  already exists: $DEST (skipping, run with --force to overwrite)"
    if [ "$1" != "--force" ]; then
        echo "Copilot adapter already installed."
        exit 0
    fi
fi

cp "$SCRIPT_DIR/../../adapters/copilot/copilot-instructions.md" "$DEST" 2>/dev/null || \
cat > "$DEST" <<'INSTRUCTIONS'
# GitHub Copilot Instructions — Risk Decision Platform

## Project context

Technical architecture exploration for Risk Decision Platform.
Real-time fraud detection: 150 TPS, p99 < 300ms latency.
Stack: Java 21 LTS baseline operativo (Java 25 LTS objetivo documentado), Gradle Kotlin DSL, Vert.x 5.0.12, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md
PoC inventory: .ai/context/poc-inventory.md
Stack versions: .ai/context/stack.md

## Non-negotiable rules

1. Java 21 LTS baseline operativo. Usar --release 21; Java 25 LTS es objetivo documentado, no build actual.
2. Clean Architecture layout: domain/{entity,repository,usecase,service,rule}, application/{usecase/<aggregate>,mapper,dto}, infrastructure/{controller,consumer,repository,resilience,time}, config/, cmd/.
3. domain/ must NOT import from application/ or infrastructure/ — ever.
4. ATDD first: write the .feature file before any production code.
5. Every request must produce trace + log + metric via OpenTelemetry. correlationId in MDC and response header.

## Available skills

Before implementing anything, check if there is a skill for it:
- .ai/primitives/skills/add-rest-endpoint.md
- .ai/primitives/skills/add-fraud-rule.md
- .ai/primitives/skills/add-kafka-publisher.md
- .ai/primitives/skills/add-otel-custom-span.md
- .ai/primitives/skills/add-resilience-pattern.md
... and 25+ more in .ai/primitives/skills/

## Available workflows

- .ai/primitives/workflows/new-feature-atdd.md
- .ai/primitives/workflows/deploy-to-k8s-local.md
- .ai/primitives/workflows/debug-trace-issue.md
... and more in .ai/primitives/workflows/

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user ownership only.
INSTRUCTIONS

echo "  created: $DEST"
echo ""
echo "Copilot adapter installed."
echo "Restart VS Code or reload the Copilot extension to pick up changes."
