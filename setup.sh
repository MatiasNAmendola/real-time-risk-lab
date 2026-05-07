#!/usr/bin/env bash
# setup.sh — Root entrypoint. Delegates to scripts/setup/setup.sh
# Usage: ./setup.sh [--dry-run] [--yes] [--only groups] [--skip groups] [--upgrade] [--verify] [--help]

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SETUP_SCRIPT="${REPO_ROOT}/scripts/setup/setup.sh"

if [[ ! -f "$SETUP_SCRIPT" ]]; then
  printf 'ERROR: Setup script not found: %s\n' "$SETUP_SCRIPT" >&2
  exit 1
fi

# Ensure setup scripts are executable
chmod +x "${SETUP_SCRIPT}"
chmod +x "${REPO_ROOT}/scripts/setup/verify.sh" 2>/dev/null || true
find "${REPO_ROOT}/scripts/setup/groups" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true
find "${REPO_ROOT}/scripts/setup/lib" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true

exec "${SETUP_SCRIPT}" "$@"
