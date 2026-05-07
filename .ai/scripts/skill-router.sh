#!/usr/bin/env bash
# skill-router.sh — Wrapper that delegates to skill-router.py
# Requires Python 3.11+

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROUTER="$SCRIPT_DIR/skill-router.py"

find_python() {
    for candidate in python3.13 python3.12 python3.11 python3; do
        if command -v "$candidate" &>/dev/null; then
            version=$("$candidate" -c 'import sys; print(sys.version_info[:2])' 2>/dev/null || echo "(0, 0)")
            # Check >= 3.11
            if "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' 2>/dev/null; then
                echo "$candidate"
                return 0
            fi
        fi
    done
    return 1
}

PYTHON=$(find_python 2>/dev/null) || {
    echo "ERROR: Python 3.11+ is required but not found." >&2
    echo "" >&2
    echo "Install it with one of:" >&2
    echo "  brew install python@3.11          # macOS via Homebrew" >&2
    echo "  sudo apt-get install python3.11   # Debian/Ubuntu" >&2
    echo "  winget install Python.Python.3.11 # Windows" >&2
    echo "  https://www.python.org/downloads/ # Official installer" >&2
    exit 1
}

exec "$PYTHON" "$ROUTER" "$@"
