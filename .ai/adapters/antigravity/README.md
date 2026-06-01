# Adapter: Google Antigravity

Google Antigravity is Google's agentic IDE based on a VS Code fork. It exposes three surfaces: Editor for synchronous coding, Manager for autonomous-agent orchestration, and browser integration.

> Confidence: medium. The product is recent and public documentation is partial.
> Do not confuse it with Gemini Code Assist or Jules.

## Files used by this adapter

| File | Purpose | Priority |
|---|---|---|
| `GEMINI.md` (root) | Antigravity-specific instructions. | High |
| `AGENTS.md` (root) | Cross-tool compatibility. | Medium |
| `.agent/rules/*.md` | Additional concern-specific rules. | Additional |
| `.gemini/antigravity/brain/` | Auto-generated knowledge base; do not edit. | Generated |

## Rule precedence

1. `GEMINI.md` — highest priority for Antigravity-specific rules.
2. `AGENTS.md` — shared with Codex, Claude Code, and other tools.
3. `.agent/rules/*.md` — additional concern-specific rules.

## Known Gemini CLI conflict

Antigravity Global Rules and Gemini CLI can both write to `~/.gemini/GEMINI.md`, causing config conflicts.
Issue: https://github.com/google-gemini/gemini-cli/issues/16058

If you use both tools, manage that file manually or choose one primary owner.

## Install through Antigravity UI

```bash
mkdir -p .agent/rules
touch GEMINI.md
# Or use the Customizations panel > + Global / + Workspace
```

## Install with this adapter

```bash
./.ai/adapters/antigravity/install.sh
```

This creates `GEMINI.md` and `.agent/rules/architecture.md`. It does not modify the existing root `AGENTS.md`.

## Format

Plain Markdown. Antigravity reads these files as direct instructions.

## Skills system

Antigravity has a separate skills system documented in Google Codelabs.
See: https://codelabs.developers.google.com/getting-started-with-antigravity-skills

## Known limitations

- Public documentation is still sparse.
- `.gemini/antigravity/brain/` is generated automatically; do not edit it manually.
- Jules is asynchronous and cloud-hosted, not local.
- The internal skills-system structure is not fully specified in public docs.

## Official documentation

- https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/
- https://codelabs.developers.google.com/getting-started-google-antigravity
- https://codelabs.developers.google.com/getting-started-with-antigravity-skills
- https://antigravity.codes/blog/user-rules
