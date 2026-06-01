# Adapter: Continue (continue.dev)

Continue is an open-source VS Code and JetBrains extension.
Global config: `~/.continue/config.yaml`.
Project override: `.continuerc.json`, merged over global config.

## Important migration note

Continue migrated from `config.json` to canonical `config.yaml`.
`config.json` is deprecated. `slashCommands` in `config.json` is deprecated; use prompt files instead.

## Files used by this adapter

| File | Purpose |
|---|---|
| `.continuerc.json` (root) | Project override merged over global config. |
| `.continue/prompts/*.prompt` | Prompt files, replacing deprecated slash commands. |
| `~/.continue/config.yaml` | Canonical global config, user-managed. |

## Global `config.yaml` format

```yaml
models:
  - provider: anthropic
    model: claude-sonnet-4-6
    apiKey: ${ANTHROPIC_API_KEY}

context:
  providers:
    - name: code
    - name: docs
    - name: diff
    - name: open

mcpServers:
  - name: filesystem
    command: npx
    args: ["@modelcontextprotocol/server-filesystem", "/allowed"]

rules:
  - Prefer TypeScript over JavaScript
  - Follow the existing code style
```

## Project `.continuerc.json` format

```json
{
  "mergeBehavior": "merge",
  "rules": ["rule 1", "rule 2"],
  "contextProviders": [],
  "models": []
}
```

`mergeBehavior: "merge"` applies on top of global config. `"overwrite"` replaces it.

## Prompt files

Location: `.continue/prompts/*.prompt` or `~/.continue/prompts/*.prompt`.
Prompt files replace the deprecated `slashCommands` array from `config.json`.
MCP prompts exposed through `mcpServers` are also registered as slash commands.

## Context providers

Use them in chat with `@`:
- `@code` — codebase symbols and files.
- `@docs` — indexed documentation.
- `@diff` — current git changes.
- `@open` — files open in the editor.

## Known limitations

- `config.json` is deprecated; new setups must use `config.yaml`.
- `.continuerc.json` is JSON, while global config is YAML.
- `.continuerc.json` does not support every global config option.
- Global config under `~/.continue/` is user-managed and not versioned here.

## Install

```bash
./.ai/adapters/continue/install.sh
```

Then install the VS Code extension: `continue.continue`.

## Official documentation

- https://docs.continue.dev/customize/overview
- https://docs.continue.dev/customize/deep-dives/configuration
- https://docs.continue.dev/reference
- https://docs.continue.dev/customize/slash-commands
