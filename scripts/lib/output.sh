#!/usr/bin/env bash
# scripts/lib/output.sh — Shared helpers for structured run output.
#
# Usage:
#   REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
#   source "$REPO_ROOT/scripts/lib/output.sh"
#   init_output "my-script-name"       # sets OUT_DIR, OUT_BASE; creates dir + latest symlink
#   ... do work, redirect to $OUT_DIR/stdout.log ...
#   finalize_output "$exit_code"       # writes meta.json, prints "Output: ..." line
#
# After init_output, the following variables are exported:
#   OUT_BASE  — $REPO_ROOT/out/<name>
#   OUT_DIR   — $OUT_BASE/<ISO-timestamp>
#   OUT_TS    — the timestamp slug used for the directory name
#
# ============================================================
# Output convention for audit scripts
# ------------------------------------------------------------
# All audit scripts route output through this helper to enforce:
#   - out/<audit-name>/<timestamp>/        : timestamped run
#   - out/<audit-name>/latest/             : symlink to latest run
#   - .gitignore: out/ is ignored
#
# Status snapshot (migrated 2026-05-12 from docs/15-script-output-audit.md):
# all in-tree audit scripts wire through this helper.
# ============================================================

# Require REPO_ROOT to be set by the caller before sourcing.
: "${REPO_ROOT:?REPO_ROOT must be set before sourcing output.sh}"

init_output() {
    local name="$1"
    OUT_TS="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
    OUT_BASE="$REPO_ROOT/out/$name"
    OUT_DIR="$OUT_BASE/$OUT_TS"
    mkdir -p "$OUT_DIR"
    # Atomic symlink update: write to a temp name then rename
    local tmp_link="$OUT_BASE/.latest.$$"
    ln -sfn "$OUT_TS" "$tmp_link"
    mv -f "$tmp_link" "$OUT_BASE/latest"
    export OUT_BASE OUT_DIR OUT_TS
}

finalize_output() {
    local exit_code="${1:-0}"
    local git_sha
    git_sha="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown")"
    cat > "$OUT_DIR/meta.json" <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "exit_code": $exit_code,
  "host": "$(hostname)",
  "git_sha": "$git_sha"
}
EOF
    echo ""
    echo "Output: $OUT_DIR"
    echo "Latest: $OUT_BASE/latest"
}
