#!/usr/bin/env bash
# Logs every Read/Glob/Grep tool invocation to .ai/logs/reads-YYYY-MM-DD.jsonl.
# Non-blocking — always exits 0 to never block the tool call.
set -u

input="${CLAUDE_TOOL_INPUT:-}"
if [[ -z "$input" ]]; then
  exit 0
fi

# Try to extract file_path from JSON input
file_path=$(echo "$input" | jq -r '.file_path // empty' 2>/dev/null)
if [[ -z "$file_path" ]]; then
  # Glob/Grep tools use 'pattern' or 'path' instead
  file_path=$(echo "$input" | jq -r '.path // .pattern // empty' 2>/dev/null)
fi

if [[ -n "$file_path" ]]; then
  ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  log_dir="$(dirname "$0")/../logs"
  mkdir -p "$log_dir" 2>/dev/null
  log_file="$log_dir/reads-$(date -u +%Y-%m-%d).jsonl"
  printf '{"ts":"%s","tool":"%s","path":"%s"}\n' \
    "$ts" "${CLAUDE_TOOL_NAME:-Read}" "$file_path" >> "$log_file"
fi

exit 0
