# Adapter: Cursor

Cursor reads MDC rules from `.cursor/rules/*.mdc` with Cursor-specific frontmatter.

## Files used by this adapter

| File | Purpose |
|---|---|
| `.cursor/rules/00-project.mdc` | Always-on project context. |
| `.cursor/rules/10-architecture.mdc` | Java architecture rules, active for Java files. |
| `.cursor/rules/20-testing.mdc` | Testing rules, active for Java tests and feature files. |

## How Cursor consumes primitives

1. Cursor reads `.cursor/rules/*.mdc` automatically.
2. Files with `alwaysApply: true` apply to every conversation.
3. Files with `globs` apply only when matching files are in context.
4. Skills are referenced by file path, for example `@.ai/primitives/skills/add-rest-endpoint.md`.

## MDC frontmatter

```yaml
---
description: Short rule description
globs: ["**/*.java", "**/build.gradle.kts"]
alwaysApply: false
---
```

## Known limitations

- MDC files do not support native imports; keep them self-contained or reference files with `@`.
- Cursor MDC globs are relative to the repository root.
- `alwaysApply: true` increases token usage; reserve it for critical rules.

## Install

```bash
./.ai/adapters/cursor/install.sh
```

## MDC format

`.mdc` is the canonical modern Cursor rule format. Legacy `.cursorrules` still works but must not be used for new project adapters.
This adapter generates only `.mdc` files.

## Official documentation

- https://docs.cursor.com/context/rules-for-ai
- https://forum.cursor.com/t/optimal-structure-for-mdc-rules-files/52260
