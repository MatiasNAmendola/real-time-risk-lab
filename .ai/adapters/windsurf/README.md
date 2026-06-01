# Adapter: Windsurf

Windsurf reads rules from `.windsurf/rules/*.md` in Wave 8+ and also supports `.windsurfrules` as a legacy fallback.

## Files used by this adapter

| File | Purpose |
|---|---|
| `.windsurf/rules/00-project.md` | Global context, `trigger: always_on`. |
| `.windsurf/rules/10-java-arch.md` | Java architecture rules, `trigger: glob` for `**/*.java`. |
| `.windsurf/rules/20-testing.md` | ATDD rules, `trigger: glob` for `**/*.feature`. |
| `.windsurfrules` (root) | Legacy fallback for pre-Wave 8 Windsurf. |

Both formats coexist. Windsurf Wave 8+ uses `.windsurf/rules/`; older versions use `.windsurfrules`.

## Activation frontmatter

```yaml
---
trigger: always_on
# or:
trigger: glob
glob: "src/**/*.java"
# or:
trigger: manual
---
```

## Size limits

Each `.windsurf/rules/` file has a 12,000 character limit.
`.windsurfrules` has no activation frontmatter; every instruction is always active.

## How Windsurf consumes primitives

1. Cascade reads `.windsurf/rules/` when the workspace opens.
2. `trigger: always_on` rules apply to every context.
3. `trigger: glob` rules apply when matching files are in context.
4. Skills are referenced by path under `.ai/primitives/skills/`.

## Known limitations

- Cascade auto-generated memories live under `~/.codeium/windsurf/memories/` and are not versioned.
- Legacy `.windsurfrules` has no activation frontmatter.
- Wave 8+ workspace rules have a 12,000 character limit per file.

## Install

```bash
./.ai/adapters/windsurf/install.sh
```

## Official documentation

- https://docs.windsurf.com/windsurf/cascade/memories
- https://docs.windsurf.com/
