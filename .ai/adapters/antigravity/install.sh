#!/usr/bin/env bash
# .ai/adapters/antigravity/install.sh
# Instala el adapter de Google Antigravity: crea GEMINI.md y .agent/rules/
# Idempotente.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Antigravity adapter install ==="
mkdir -p "$REPO_ROOT/.agent/rules"

# GEMINI.md en root — mayor prioridad para Antigravity
GEMINI_FILE="$REPO_ROOT/GEMINI.md"
if [ -f "$GEMINI_FILE" ]; then
    echo "  already exists: $GEMINI_FILE (skipping)"
else
cat > "$GEMINI_FILE" <<'EOF'
# GEMINI.md — Risk Decision Platform (Google Antigravity)

## Project

Technical architecture exploration for Risk Decision Platform.
Real-time fraud detection: 150 TPS, p99 < 300ms.
Stack: Java 25 LTS, Vert.x 5.0.12, Maven, Postgres 16, Valkey 8, Redpanda, k3d/OrbStack.

Full context: .ai/context/architecture.md

## Non-negotiable rules

1. Java 25 LTS only. Do NOT downgrade to 21. Use --release 25.
2. Clean Architecture: domain/ does NOT import from application/ or infrastructure/.
3. ATDD first: write .feature before production code.
4. Every request must produce trace + log + metric via OpenTelemetry.
5. correlationId in MDC and response header X-Correlation-Id.

## Skills and rules

Before implementing anything, check .ai/primitives/skills/ and .ai/primitives/rules/.

## Do not touch

poc/, tests/, cli/, docs/, vault/ — user ownership only.
EOF
    echo "  created: $GEMINI_FILE"
fi

# Note: AGENTS.md is already created by the main .ai/ agent
# Antigravity reads AGENTS.md since v1.20.3 (March 2026)
if [ -f "$REPO_ROOT/AGENTS.md" ]; then
    echo "  verified: AGENTS.md exists (Antigravity reads this since v1.20.3)"
else
    echo "  WARNING: AGENTS.md not found. Antigravity reads AGENTS.md since v1.20.3."
fi

# .agent/rules/ — adicionales organizados por concern
cat > "$REPO_ROOT/.agent/rules/architecture.md" <<'EOF'
# Architecture Rules

## Java layout (canonical)

domain/{entity,repository,usecase,service,rule}
application/{usecase/<aggregate>,mapper,dto}
infrastructure/{controller,consumer,repository,resilience,time}
config/ cmd/

## Dependency rule

domain/ must NOT import from application/ or infrastructure/ — ever.
Outbound ports: interface in domain/repository/, impl in infrastructure/repository/.

See: .ai/primitives/rules/architecture-clean.md
EOF

echo "  created: $REPO_ROOT/.agent/rules/architecture.md"

echo ""
echo "Antigravity adapter installed."
echo "  GEMINI.md:       $GEMINI_FILE"
echo "  .agent/rules/:   $REPO_ROOT/.agent/rules/"
echo ""
echo "IMPORTANT: Antigravity reads both GEMINI.md and AGENTS.md (since v1.20.3)."
echo "GEMINI.md takes priority over AGENTS.md in conflicts."
echo ""
echo "WARNING: Antigravity and Gemini CLI both write to ~/.gemini/GEMINI.md"
echo "  This can cause conflicts if both tools are used in the same environment."
echo "  See: https://github.com/google-gemini/gemini-cli/issues/16058"
