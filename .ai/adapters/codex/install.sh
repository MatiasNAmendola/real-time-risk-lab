#!/usr/bin/env bash
# .ai/adapters/codex/install.sh
# Installs the Codex adapter by creating .codex/AGENTS.md as a symlink to AGENTS.md.
# Idempotent.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== Codex adapter install ==="
mkdir -p "$REPO_ROOT/.codex"

# Create .codex/AGENTS.md -> ../AGENTS.md.
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

# Verify that root AGENTS.md exists.
if [ -f "$REPO_ROOT/AGENTS.md" ]; then
    echo "  verified: $REPO_ROOT/AGENTS.md exists"
else
    echo "  WARNING: $REPO_ROOT/AGENTS.md not found. Run install from the repo root after creating it."
fi

echo ""
echo "Codex adapter installed."
echo "Codex CLI will read AGENTS.md automatically when run in this directory."
