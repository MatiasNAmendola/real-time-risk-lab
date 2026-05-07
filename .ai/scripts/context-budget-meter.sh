#!/usr/bin/env bash
# context-budget-meter.sh — Claude Code UserPromptSubmit hook (observer).
# Estimates token budget consumed and logs to .ai/logs/context-budget-<date>.jsonl.
# Observes only — never blocks the prompt.
# Principle: Every decision is observable.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
LOGS_DIR="$REPO_ROOT/.ai/logs"
BUDGETS_FILE="$REPO_ROOT/.ai/state/budgets.yaml"
DATE="$(date -u +%Y-%m-%d)"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
LOG_FILE="$LOGS_DIR/context-budget-${DATE}.jsonl"

mkdir -p "$LOGS_DIR"

# Read the prompt from CLAUDE_USER_PROMPT env var (set by Claude Code hook system)
USER_PROMPT="${CLAUDE_USER_PROMPT:-}"
PROMPT_LEN=${#USER_PROMPT}

# Token estimate: chars / 4 (rough approximation for English/code)
ESTIMATED_TOKENS=$(( (PROMPT_LEN + 3) / 4 ))

# Try tiktoken if available (Python)
TIKTOKEN_TOKENS=""
if python3 -c "import tiktoken" 2>/dev/null; then
    TIKTOKEN_TOKENS=$(python3 - <<PYEOF
import tiktoken, sys, os
try:
    enc = tiktoken.encoding_for_model("cl100k_base")
    prompt = os.environ.get("CLAUDE_USER_PROMPT", "")
    count = len(enc.encode(prompt))
    print(count)
except Exception:
    pass
PYEOF
    )
fi

# Read budget config (simple key: value parse, no pyyaml)
BUDGET_USER=12000
BUDGET_TURN=8000
WARN_PCT=80
BLOCK_PCT=95
if [ -f "$BUDGETS_FILE" ]; then
    BUDGET_USER=$(python3 -c "
import re, sys
with open('$BUDGETS_FILE') as f:
    content = f.read()
m = re.search(r'user:\s*(\d+)', content)
print(m.group(1) if m else 12000)
" 2>/dev/null || echo 12000)
    WARN_PCT=$(python3 -c "
import re
with open('$BUDGETS_FILE') as f:
    content = f.read()
m = re.search(r'warn_at_pct:\s*(\d+)', content)
print(m.group(1) if m else 80)
" 2>/dev/null || echo 80)
fi

# Compute usage pct
USE_TOKENS="${TIKTOKEN_TOKENS:-$ESTIMATED_TOKENS}"
PCT=0
if [ "$BUDGET_USER" -gt 0 ]; then
    PCT=$(( (USE_TOKENS * 100) / BUDGET_USER ))
fi

# Determine status
STATUS="ok"
if [ "$PCT" -ge "$WARN_PCT" ]; then
    STATUS="warn"
fi

# Append log entry
python3 - <<PYEOF
import json, os
entry = {
    "ts": "$TS",
    "estimated_tokens": $USE_TOKENS,
    "prompt_chars": $PROMPT_LEN,
    "budget_user": $BUDGET_USER,
    "pct_used": $PCT,
    "status": "$STATUS",
    "tiktoken_available": bool("$TIKTOKEN_TOKENS"),
}
log_file = "$LOG_FILE"
with open(log_file, "a") as f:
    f.write(json.dumps(entry) + "\n")
PYEOF

# Warn to stderr (does not block) if over threshold
if [ "$STATUS" = "warn" ]; then
    echo "[context-budget-meter] WARNING: prompt ~${PCT}% of user budget (${USE_TOKENS} / ${BUDGET_USER} tokens)" >&2
fi

# Always exit 0 — observer only, never blocks
exit 0
