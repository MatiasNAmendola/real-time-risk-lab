#!/usr/bin/env bash
# session-handoff.sh — Claude Code Stop hook.
# Generates vault/01-Sessions/handoffs/<ts>-handoff.md from session logs.
# Principle: Sessions are first-class.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
HANDOFF_DIR="$REPO_ROOT/vault/01-Sessions/handoffs"
HANDOFF_FILE="$HANDOFF_DIR/${TS}-handoff.md"
LOGS_DIR="$REPO_ROOT/.ai/logs"

mkdir -p "$HANDOFF_DIR"

# ---- gather raw data ----

SKILL_LOGS=("$LOGS_DIR"/skill-routing-*.jsonl)

# Topics: infer from skill routing logs
TOPICS_COVERED=""
if ls "$LOGS_DIR"/skill-routing-*.jsonl &>/dev/null; then
    TOPICS_COVERED=$(python3 - <<'PYEOF'
import json, glob, os, sys
pattern = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "skill-routing-*.jsonl")
skills = {}
for path in sorted(glob.glob(pattern)):
    try:
        with open(path) as f:
            for line in f:
                try:
                    d = json.loads(line)
                    s = d.get("top_skill", "")
                    if s:
                        skills[s] = skills.get(s, 0) + 1
                except Exception:
                    pass
    except Exception:
        pass
for s, n in sorted(skills.items(), key=lambda x: -x[1])[:10]:
    print(f"- {s} (invoked {n}x)")
PYEOF
    )
fi

# Files modified: from skill routing tool targets
FILES_MODIFIED=""
if ls "$LOGS_DIR"/skill-routing-*.jsonl &>/dev/null; then
    FILES_MODIFIED=$(python3 - <<'PYEOF'
import json, glob, os, collections
pattern = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "skill-routing-*.jsonl")
targets = collections.Counter()
for path in sorted(glob.glob(pattern)):
    try:
        with open(path) as f:
            for line in f:
                try:
                    d = json.loads(line)
                    t = d.get("target", "")
                    if t and t != "unknown":
                        targets[t] += 1
                except Exception:
                    pass
    except Exception:
        pass
for t, n in targets.most_common(15):
    print(f"- {t}")
PYEOF
    )
fi

# Agents launched: from workflow runs
AGENTS_LAUNCHED=0
TOOLS_INVOKED=0
if ls "$LOGS_DIR"/skill-routing-*.jsonl &>/dev/null; then
    TOOLS_INVOKED=$(python3 -c "
import json, glob, os
pattern = os.path.join('$LOGS_DIR', 'skill-routing-*.jsonl')
count = 0
for p in glob.glob(pattern):
    try:
        with open(p) as f:
            count += sum(1 for l in f if l.strip())
    except Exception:
        pass
print(count)
" 2>/dev/null || echo 0)
fi

# Token estimate from budget logs
TOKEN_ESTIMATE="unknown"
if ls "$LOGS_DIR"/context-budget-*.jsonl &>/dev/null 2>&1; then
    TOKEN_ESTIMATE=$(python3 - <<'PYEOF'
import json, glob, os
pattern = os.path.join(os.environ.get("LOGS_DIR", ".ai/logs"), "context-budget-*.jsonl")
total = 0
count = 0
for path in sorted(glob.glob(pattern)):
    try:
        with open(path) as f:
            for line in f:
                try:
                    d = json.loads(line)
                    total += d.get("estimated_tokens", 0)
                    count += 1
                except Exception:
                    pass
    except Exception:
        pass
if count:
    print(f"~{total:,} tokens across {count} turns")
else:
    print("unknown")
PYEOF
    )
fi

# ---- write handoff document ----

cat > "$HANDOFF_FILE" <<HANDOFF
# Session Handoff — $TS

## Topics Covered

${TOPICS_COVERED:-"(no skill-routing logs found)"}

## Decisions Made

(Decisions are persisted in engram via mem_save during the session.
Search engram with mem_search to recover them.)

## Files Modified

${FILES_MODIFIED:-"(no file modification logs found)"}

## Next Actions Suggested

- Review any TODO/FIXME comments introduced during this session.
- Run the full test suite if code was modified.
- Archive completed changes via sdd-archive if SDD workflow was active.

## Session Stats

- Estimated tokens: $TOKEN_ESTIMATE
- Tools invoked (logged): $TOOLS_INVOKED
- Agents launched (background): $AGENTS_LAUNCHED
- Handoff written: $TS

## Log Sources

- Skill routing: $LOGS_DIR/skill-routing-*.jsonl
- Workflow runs: $LOGS_DIR/workflow-runs/*.jsonl
- Context budget: $LOGS_DIR/context-budget-*.jsonl
HANDOFF

echo "Handoff written: $HANDOFF_FILE"
