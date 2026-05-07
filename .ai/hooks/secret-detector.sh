#!/usr/bin/env bash
# PreToolUse hook — secret detection and redaction
# Mode: redact-and-allow (dual-mode: redact when possible, deny when entire input is secret)
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
LOG_DIR="$REPO_ROOT/.ai/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/secret-detections-$(date -u +%Y-%m-%d).jsonl"

PAYLOAD="$(cat)"

TOOL_NAME="${CLAUDE_TOOL_NAME:-}"

# Only process relevant tools
case "$TOOL_NAME" in
  Bash|Edit|Write|MultiEdit) ;;
  *) printf '%s' "$PAYLOAD"; exit 0 ;;
esac

# Pattern definitions as parallel arrays: name and regex
# Patterns use Python re syntax (compatible with grep -E for detection;
# redaction done via Python to avoid BSD sed alternation limitations)
PAT_NAMES=(
  "aws_access_key"
  "aws_session_key"
  "github_pat"
  "slack_token"
  "slack_webhook"
  "stripe_live"
  "openai_anthropic_key"
  "npm_token"
  "private_key_header"
  "jwt"
  "db_conn_string"
)

PAT_REGEXES=(
  'AKIA[0-9A-Z]{16}'
  'ASIA[0-9A-Z]{16}'
  'gh[oprusu]_[A-Za-z0-9_]{36,}'
  'xox[abprs]-[A-Za-z0-9-]{10,}'
  'hooks\.slack\.com/services/T[A-Z0-9]+/B[A-Z0-9]+/[A-Za-z0-9]+'
  'sk_live_[A-Za-z0-9]{24,}'
  'sk-(proj-|ant-)?[A-Za-z0-9_-]{20,}'
  'npm_[A-Za-z0-9]{36,}'
  '-----BEGIN [A-Z ]*PRIVATE KEY-----'
  'eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'
  '(postgres|mysql|mongodb|redis)://[^:]+:[^@]+@'
)

# Get the field names to check for a given tool
get_fields() {
  local tool="$1"
  case "$tool" in
    Bash)      echo "command" ;;
    Edit)      echo "new_string" ;;
    Write)     echo "content" ;;
    MultiEdit) echo "new_string" ;;
  esac
}

FIELDS="$(get_fields "$TOOL_NAME")"

# Use Python for regex application — avoids BSD sed alternation issues
# and handles multi-line content safely.
# Writes: redacted_value to stdout, matched_names (space-separated) to a temp file
MATCH_FILE="$(mktemp)"
REDACTED_FILE="$(mktemp)"

# Build Python script that applies all patterns
python3_redact() {
  local value="$1"
  local match_out="$2"
  local redacted_out="$3"

  python3 - "$value" "$match_out" "$redacted_out" << 'PYEOF'
import sys
import re

value = sys.argv[1]
match_out = sys.argv[2]
redacted_out = sys.argv[3]

patterns = [
    ("aws_access_key",       r"AKIA[0-9A-Z]{16}"),
    ("aws_session_key",      r"ASIA[0-9A-Z]{16}"),
    ("github_pat",           r"gh[oprusu]_[A-Za-z0-9_]{36,}"),
    ("slack_token",          r"xox[abprs]-[A-Za-z0-9-]{10,}"),
    ("slack_webhook",        r"hooks\.slack\.com/services/T[A-Z0-9]+/B[A-Z0-9]+/[A-Za-z0-9]+"),
    ("stripe_live",          r"sk_live_[A-Za-z0-9]{24,}"),
    ("openai_anthropic_key", r"sk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}"),
    ("npm_token",            r"npm_[A-Za-z0-9]{36,}"),
    ("private_key_header",   r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    ("jwt",                  r"eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"),
    ("db_conn_string",       r"(?:postgres|mysql|mongodb|redis)://[^:]+:[^@]+@"),
]

result = value
matched = []

for name, pattern in patterns:
    rx = re.compile(pattern)
    if rx.search(result):
        result = rx.sub("[REDACTED]", result)
        matched.append(name)

with open(redacted_out, "w") as f:
    f.write(result)

with open(match_out, "w") as f:
    f.write(" ".join(matched))
PYEOF
}

MUTATED_PAYLOAD="$PAYLOAD"
ALL_MATCHED_PATTERNS=""
ALL_FIELDS_REDACTED=""
ANY_MATCH=false
ALL_ENTIRELY_REDACTED=true
HAS_FIELD=false

for field in $FIELDS; do
  # Extract field value via jq
  field_value="$(printf '%s' "$MUTATED_PAYLOAD" | jq -r --arg f "$field" '.[$f] // empty' 2>/dev/null)" || continue
  if [ -z "$field_value" ]; then
    continue
  fi

  HAS_FIELD=true

  python3_redact "$field_value" "$MATCH_FILE" "$REDACTED_FILE"
  matched="$(cat "$MATCH_FILE")"
  redacted_value="$(cat "$REDACTED_FILE")"

  if [ -n "$matched" ]; then
    ANY_MATCH=true
    ALL_MATCHED_PATTERNS="$ALL_MATCHED_PATTERNS $matched"
    ALL_FIELDS_REDACTED="$ALL_FIELDS_REDACTED $field"

    # Check if the field was entirely secrets (only [REDACTED] tokens + whitespace remain)
    stripped="$(printf '%s' "$redacted_value" | sed 's/\[REDACTED\]//g' | tr -d '[:space:]')"
    if [ -n "$stripped" ]; then
      ALL_ENTIRELY_REDACTED=false
    fi

    # Update payload with redacted value
    MUTATED_PAYLOAD="$(printf '%s' "$MUTATED_PAYLOAD" | jq --arg f "$field" --arg v "$redacted_value" '.[$f] = $v')"
  else
    ALL_ENTIRELY_REDACTED=false
  fi
done

rm -f "$MATCH_FILE" "$REDACTED_FILE"

# No secrets found — pass through unchanged
if ! $ANY_MATCH; then
  printf '%s' "$PAYLOAD"
  exit 0
fi

# Collect unique pattern names and field names for log
UNIQUE_PATTERNS="$(printf '%s' "$ALL_MATCHED_PATTERNS" | tr ' ' '\n' | grep -v '^$' | sort -u | tr '\n' ',' | sed 's/,$//')"
UNIQUE_FIELDS="$(printf '%s' "$ALL_FIELDS_REDACTED" | tr ' ' '\n' | grep -v '^$' | sort -u | tr '\n' ',' | sed 's/,$//')"

# Forensic: first 8 chars of original first matched field value (correlation, NOT full secret)
FIRST_FIELD="$(printf '%s' "$ALL_FIELDS_REDACTED" | tr ' ' '\n' | grep -v '^$' | head -1)"
FIRST_VALUE="$(printf '%s' "$PAYLOAD" | jq -r --arg f "$FIRST_FIELD" '.[$f] // ""' 2>/dev/null | head -c 8 | tr -d '\n')"

TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

printf '{"ts":"%s","tool":"%s","fields_redacted":"%s","pattern_names_matched":"%s","first_8_chars":"%s"}\n' \
  "$TS" "$TOOL_NAME" "$UNIQUE_FIELDS" "$UNIQUE_PATTERNS" "$FIRST_VALUE" \
  >> "$LOG_FILE"

# Deny if entire input was secrets
if $ALL_ENTIRELY_REDACTED && $HAS_FIELD; then
  printf '{"hookSpecificOutput":{"permissionDecision":"deny","reason":"tool input was entirely secrets"}}'
  exit 2
fi

# Allow with redacted input
printf '{"hookSpecificOutput":{"permissionDecision":"allow","updatedInput":%s}}' \
  "$(printf '%s' "$MUTATED_PAYLOAD" | jq -c '.')"
