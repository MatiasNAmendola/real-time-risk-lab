#!/usr/bin/env bash
# .ai/adapters/codex/install.sh
# Instala el adapter de Codex: crea .codex/AGENTS.md como symlink a AGENTS.md
# Idempotente.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Codex adapter install ==="
mkdir -p "$REPO_ROOT/.codex"

# Crear symlink .codex/AGENTS.md -> ../AGENTS.md
LINK="$REPO_ROOT/.codex/AGENTS.md"
TARGET="../AGENTS.md"

if [ -L "$LINK" ]; then
    echo "  already exists (symlink): $LINK"
elif [ -f "$LINK" ]; then
    echo "  already exists (file): $LINK (not replacing)"
else
    ln -s "$TARGET" "$LINK"
    echo "  created symlink: $LINK -> $TARGET"
fi

# Verificar que AGENTS.md existe en raiz
if [ -f "$REPO_ROOT/AGENTS.md" ]; then
    echo "  verified: $REPO_ROOT/AGENTS.md exists"
else
    echo "  WARNING: $REPO_ROOT/AGENTS.md not found. Run install from repo root after creating it."
fi

echo ""
echo "Codex adapter installed."
echo "Codex CLI will read AGENTS.md automatically when run in this directory."
