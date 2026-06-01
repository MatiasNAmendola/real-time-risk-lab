# Adapter: Claude Code

Claude Code is Anthropic's CLI. It reads `CLAUDE.md` at the repository root and supports hooks through `.claude/settings.json`.

## Files used by this adapter

| File | Purpose |
|---|---|
| `CLAUDE.md` (root) | Main entrypoint. Uses `@AGENTS.md` to import shared project context. |
| `.claude/settings.json` | Hooks, permissions, and harness config. |
| `.claude/agents/*.md` | Skill-specific sub-agents, one per workflow/skill. |

## How Claude Code consumes primitives

1. Claude Code reads `CLAUDE.md` when the repo opens.
2. `CLAUDE.md` includes `@AGENTS.md` using Claude Code import syntax.
3. Users can reference skills with slash commands or direct file references.
4. Hooks in `.claude/settings.json` run on harness events.
5. Sub-agents in `.claude/agents/` can be invoked for focused tasks.

## Known limitations

- Slash commands require manual setup in `.claude/settings.json` or harness skills.
- `@import` works for repository files, not URLs.
- Hook commands must be fast to avoid blocking the agent loop.

## Install

```bash
./.ai/adapters/claude-code/install.sh
```

The script creates one `.claude/agents/` sub-agent per skill.

## Recommended hooks

See `.ai/primitives/hooks/` for each hook contract.

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "bash .ai/scripts/check-secrets.sh" }]
      }
    ]
  }
}
```
