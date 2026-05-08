#!/usr/bin/env bash
# session-bootstrap.sh — Session startup script for AI agents working on real-time-risk-lab.
# Displays primitive inventory and recent usage stats.
#
# Usage:
#   bash .ai/scripts/session-bootstrap.sh
#
# This script is called via Claude Code's SessionStart hook (if configured) or
# can be run manually at the start of any agent session.
#
# Note: This script coexists with engram-bootstrap.sh. Run both if using Engram.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

echo "=== Real-Time Risk Lab ==="
echo
echo "Project: real-time-risk-lab"
echo "Active session: $(date -u +%Y-%m-%dT%H-%M-%SZ)"
echo
echo "PRIMITIVES AVAILABLE:"
echo "  Skills:    $(ls .ai/primitives/skills/*.md 2>/dev/null | wc -l | tr -d ' ')"
echo "  Rules:     $(ls .ai/primitives/rules/*.md 2>/dev/null | wc -l | tr -d ' ')"
echo "  Workflows: $(ls .ai/primitives/workflows/*.md 2>/dev/null | wc -l | tr -d ' ')"
echo "  Hooks:     $(ls .ai/primitives/hooks/*.md 2>/dev/null | wc -l | tr -d ' ')"
echo
echo "USAGE LAST 7 DAYS:"
python3 .ai/scripts/usage-stats.py --last 7 --no-color 2>/dev/null || echo "  (no usage logs yet)"
echo
echo "PROTOCOL: read CLAUDE.md before any Edit. Run skill-router first."
echo "  Quick start: python3 .ai/scripts/skill-router.py --top 3 \"<your task description>\""
echo "  Workflows:   python3 .ai/scripts/workflow-runner.py --dry-run <workflow-name>"
echo "  Telemetry:   python3 .ai/scripts/usage-stats.py"
