@AGENTS.md

---

# CLAUDE.md — Claude Code-specific config

This file extends `AGENTS.md` with Claude Code harness-specific guidance.

## Recommended hooks (`.claude/settings.json`)

Add hooks like these to automate safety checks:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash .ai/scripts/check-secrets.sh 2>/dev/null || true"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash .ai/scripts/run-module-tests.sh 2>/dev/null || true"
          }
        ]
      }
    ]
  }
}
```

Full hook contracts: `.ai/primitives/hooks/`

## Available sub-agents

Sub-agents in `.claude/agents/` map to atomic skills:

```bash
# Install all sub-agents:
./.ai/adapters/claude-code/install.sh

# Example prompts:
# "use the add-rest-endpoint skill"
# "run the new-feature-atdd workflow"
```

## Suggested slash-command phrasing

After installing sub-agents, invoke them with prompts such as:
- "use the add-fraud-rule sub-agent"
- "run add-otel-custom-span for the evaluation use case"
- "bootstrap-new-poc for a new CQRS PoC"

## Engram MCP

This project uses Engram MCP. At session start:

1. `mem_current_project()` — detect the project.
2. `mem_context(project: "real-time-risk-lab")` — load context.
3. `mem_search(query: "risk-platform current state")` — recover current state.

At session end, `mem_session_summary(...)` is mandatory.

See `.ai/context/engram.md` and `.ai/primitives/hooks/session-start-engram-load.md`.

## Verify the primitive system

```bash
./.ai/scripts/verify-primitives.sh
```

## Primitive-first protocol (mandatory)

Before launching a sub-agent or making a significant Edit/Write:

1. Run `python3 .ai/scripts/skill-router.py --top 3 "<task description>"`.
2. Read the top skill. If confidence is > 0.5 and intent matches, cite it in the prompt: `SKILL: Load .ai/primitives/skills/<name>.md as your guide.`
3. For multi-step tasks, run `python3 .ai/scripts/workflow-runner.py --dry-run <workflow>` first.
4. Log the routing decision in `.ai/logs/skill-routing-YYYY-MM-DD.jsonl`.
5. If no skill applies, add one before improvising.

The Claude `PreToolUse` hook automates steps 1 and 4. Manual `workflow-runner` invocation remains the orchestrator's responsibility.

```bash
python3 .ai/scripts/skill-router.py --top 3 "add a Kafka consumer"
python3 .ai/scripts/workflow-runner.py --dry-run add-comm-pattern
python3 .ai/scripts/usage-stats.py
```

## Claude Code working rules

1. Before implementation: read the matching rule and skill.
2. Before commit: verify that relevant tests pass.
3. Do not edit `poc/`, `tests/`, `cli/`, `docs/`, or `vault/` unless the task requires it.
4. Save architecture decisions, bug fixes, and discoveries to Engram immediately when available.
5. Always call `mem_session_summary` at session end when Engram tools are available.
