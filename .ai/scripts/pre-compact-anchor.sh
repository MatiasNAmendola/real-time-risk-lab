#!/usr/bin/env bash
# pre-compact-anchor.sh — Claude Code PreCompact hook.
# Writes .ai/state/anchor-<sessionid>.md before context compression.
# Principle: Sessions are first-class.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
STATE_DIR="$REPO_ROOT/.ai/state"
LOGS_DIR="$REPO_ROOT/.ai/logs"
TS="$(date -u +%Y%m%dT%H%M%SZ)"

# Claude Code exposes CLAUDE_SESSION_ID when available
SESSION_ID="${CLAUDE_SESSION_ID:-${TS}}"
ANCHOR_FILE="$STATE_DIR/anchor-${SESSION_ID}.md"

mkdir -p "$STATE_DIR"

# Recent skill invocations (last 20)
RECENT_SKILLS=""
if ls "$LOGS_DIR"/skill-routing-*.jsonl &>/dev/null; then
    RECENT_SKILLS=$(python3 - <<'PYEOF'
import json, glob, os
pattern = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "skill-routing-*.jsonl")
entries = []
for path in sorted(glob.glob(pattern)):
    try:
        with open(path) as f:
            entries.extend(line.strip() for line in f if line.strip())
    except Exception:
        pass
for line in entries[-20:]:
    try:
        d = json.loads(line)
        print(f"- [{d.get('ts','')}] {d.get('tool','')} -> {d.get('top_skill','')} (score={d.get('score',0)})")
    except Exception:
        pass
PYEOF
    )
fi

# Files in flight (recently touched)
FILES_IN_FLIGHT=""
if ls "$LOGS_DIR"/skill-routing-*.jsonl &>/dev/null; then
    FILES_IN_FLIGHT=$(python3 - <<'PYEOF'
import json, glob, os, collections
pattern = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "skill-routing-*.jsonl")
targets = collections.OrderedDict()
for path in sorted(glob.glob(pattern)):
    try:
        with open(path) as f:
            for line in f:
                try:
                    d = json.loads(line)
                    t = d.get("target", "")
                    if t and t != "unknown":
                        targets[t] = d.get("ts", "")
                except Exception:
                    pass
    except Exception:
        pass
for t, ts in list(targets.items())[-10:]:
    print(f"- {t} (last touched: {ts})")
PYEOF
    )
fi

# Check for any pending agent-bus messages
PENDING_MESSAGES=""
BUS_FILE="$LOGS_DIR/agent-bus.jsonl"
if [ -f "$BUS_FILE" ]; then
    PENDING_MESSAGES=$(python3 - <<'PYEOF'
import json, os, sys
bus_file = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "agent-bus.jsonl")
if not os.path.exists(bus_file):
    sys.exit(0)
rows = []
acks = {}
with open(bus_file) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
            if d.get("_record_type") == "ack":
                acks[d["message_id"]] = d["status"]
            else:
                rows.append(d)
        except Exception:
            pass
pending = [r for r in rows if acks.get(r.get("message_id",""), "pending") == "pending"]
if pending:
    for r in pending[-5:]:
        print(f"- {r.get('sender','')} -> {r.get('recipient','')} [{r.get('context_mode','')}]: {r.get('intent','')[:60]}")
PYEOF
    )
fi

cat > "$ANCHOR_FILE" <<ANCHOR
# Pre-Compact Anchor — $TS

Session: $SESSION_ID
Written at: $TS

## Current State Summary

This anchor was written automatically before context compaction.
The session-bootstrap script reads this file on the next turn to reconstruct context.

## Recent Skill Invocations

${RECENT_SKILLS:-"(none logged)"}

## Files In Flight

${FILES_IN_FLIGHT:-"(none detected)"}

## Pending Agent Bus Messages

${PENDING_MESSAGES:-"(none)"}

## Critical Decisions

(Decisions persisted in engram via mem_save — search with mem_search on session resume.)

## Background Agents

(Check .ai/logs/workflow-runs/ for in-flight workflow state.)

## Resume Instructions

On next session start, session-bootstrap.sh will load this anchor.
To recover full context:
1. Call mem_context to get recent engram entries.
2. Read files listed under "Files In Flight".
3. Process any pending agent-bus messages.
ANCHOR

echo "Anchor written: $ANCHOR_FILE"
