#!/usr/bin/env bash
# skill-route-from-tool.sh — Extract intent from a Claude tool call JSON and route to skill-router.
#
# Usage (stdin):
#   echo '{"file_path":"src/...","new_string":"..."}' | ./skill-route-from-tool.sh
#
# Output (stdout): JSON from skill-router
# Exit: always 0 (never blocks tool calls)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Read stdin into variable
INPUT="$(cat)"

# Extract intent heuristically from tool input JSON
# Priority: file_path > description > new_string (truncated)
extract_intent() {
    local json="$1"

    # Try file_path first — most informative
    local path
    path="$(echo "$json" | python3 -c "
import sys, json, re
try:
    data = json.load(sys.stdin)
    p = data.get('file_path', data.get('path', ''))
    if p:
        # Convert path to words: strip dirs, remove extension, split camelCase/snake_case
        base = re.sub(r'.*/', '', p)
        base = re.sub(r'\.[^.]+$', '', base)
        base = re.sub(r'([A-Z])', r' \1', base).lower()
        base = re.sub(r'[-_]', ' ', base).strip()
        print(base)
    else:
        print('')
except Exception:
    print('')
" 2>/dev/null)"

    # Try description field
    local desc
    desc="$(echo "$json" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('description', ''))
except Exception:
    print('')
" 2>/dev/null)"

    # Try new_string (first 120 chars)
    local new_str
    new_str="$(echo "$json" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    s = data.get('new_string', data.get('content', ''))[:120]
    print(s)
except Exception:
    print('')
" 2>/dev/null)"

    # Build intent: prefer path + desc combo
    local intent=""
    if [ -n "$path" ] && [ -n "$desc" ]; then
        intent="$desc $path"
    elif [ -n "$desc" ]; then
        intent="$desc"
    elif [ -n "$path" ]; then
        intent="$path $new_str"
    elif [ -n "$new_str" ]; then
        intent="$new_str"
    else
        intent="code edit"
    fi

    # Trim and truncate to 200 chars
    echo "$intent" | tr '\n' ' ' | cut -c1-200
}

INTENT="$(extract_intent "$INPUT")"

# Call skill-router with JSON output, top 3
cd "$REPO_ROOT"
python3 .ai/scripts/skill-router.py --json --top 3 "$INTENT" 2>/dev/null || echo '{"query":"'"$INTENT"'","results":[],"error":"skill-router failed"}'
