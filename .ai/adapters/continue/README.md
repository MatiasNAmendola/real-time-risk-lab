# Adapter: Continue (continue.dev)

Continue es una extension open source para VS Code/JetBrains.
Config global: `~/.continue/config.yaml` (formato canonico desde 2025).
Override de proyecto: `.continuerc.json` (merge sobre config global).

## CAMBIO IMPORTANTE

Continue migro de `config.json` a `config.yaml` como formato canonico.
`config.json` esta deprecated. `slashCommands` en config.json esta deprecated — usar prompt files.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `.continuerc.json` (root) | Override de proyecto. Merge sobre config global. |
| `.continue/prompts/*.prompt` | Prompt files (reemplazo de slashCommands deprecated) |
| `~/.continue/config.yaml` | Config global canonica (gestionada por el usuario) |

## Formato de config.yaml global (nuevo)

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

## Formato de .continuerc.json (override de proyecto)

```json
{
  "mergeBehavior": "merge",
  "rules": ["rule 1", "rule 2"],
  "contextProviders": [],
  "models": []
}
```

`mergeBehavior: "merge"` aplica encima del config global; `"overwrite"` reemplaza todo.

## Prompt files (reemplazo de slashCommands)

Ubicacion: `.continue/prompts/*.prompt` o `~/.continue/prompts/*.prompt`.
Los prompt files reemplazan el array `slashCommands` de config.json (deprecated).
MCP prompts via `mcpServers` tambien se registran automaticamente como slash commands.

## Context providers (@)

Se referencian con `@` en el chat:
- `@code` — simbolos y archivos del codebase
- `@docs` — documentacion indexada
- `@diff` — cambios actuales en git
- `@open` — archivos abiertos en el editor

## Limitaciones conocidas

- `config.json` esta deprecated — nuevos setups deben usar `config.yaml`.
- `.continuerc.json` es JSON (inconsistente con el nuevo `config.yaml`).
- `.continuerc.json` no soporta todas las opciones de config global.
- Config global en `~/.continue/` no se versiona con el proyecto.

## Instalar

```bash
./.ai/adapters/continue/install.sh
```

Luego instalar la extension: `continue.continue` en VS Code.

## Documentacion oficial

- https://docs.continue.dev/customize/overview
- https://docs.continue.dev/customize/deep-dives/configuration
- https://docs.continue.dev/reference (config.yaml reference)
- https://docs.continue.dev/customize/slash-commands
