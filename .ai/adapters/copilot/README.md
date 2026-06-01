# Adapter: GitHub Copilot

GitHub Copilot reads `.github/copilot-instructions.md` as repository-wide instructions and supports per-file or per-language instructions in `.github/instructions/`.

## Files used by this adapter

| File | Purpose |
|---|---|
| `.github/copilot-instructions.md` | Global repository instructions without frontmatter. |
| `.github/instructions/*.instructions.md` | Per-file/per-language instructions with frontmatter. |

## Instruction precedence

1. Personal user instructions.
2. Repository instructions from `.github/copilot-instructions.md`.
3. Organization instructions.

## Per-file format

```yaml
---
applyTo: "**/*.java"
# Multiple patterns:
# applyTo: "**/*.java,**/build.gradle.kts"
# Agent exclusions:
# excludeAgent: "code-review"
# excludeAgent: "coding-agent"
---
# Java-specific instructions
```

Fields:
- `applyTo`: target glob.
- `excludeAgent`: `"code-review"` or `"coding-agent"`.

## Important

`.github/copilot-instructions.md` has no frontmatter. Its whole body is plain instruction text.
Files under `.github/instructions/` do use frontmatter with `applyTo`.

## How Copilot consumes primitives

1. Copilot reads `.github/copilot-instructions.md` when the repo opens.
2. Users can reference extra files in chat, for example `#file:.ai/primitives/skills/add-rest-endpoint.md`.
3. Per-file instructions apply automatically by language or glob.

## Known limitations

- `.github/copilot-instructions.md` does not support frontmatter.
- Official size limits are not clearly documented; keep instructions concise.
- Copilot CLI has a separate instruction system.

## Install

```bash
./.ai/adapters/copilot/install.sh
```

## Official documentation

- https://docs.github.com/copilot/customizing-copilot/adding-custom-instructions-for-github-copilot
- https://github.blog/changelog/2025-11-12-copilot-code-review-and-coding-agent-now-support-agent-specific-instructions/
