# Adapter: Claude Code

Claude Code es el CLI oficial de Anthropic. Lee `CLAUDE.md` en la raiz del repo y soporta hooks via `.claude/settings.json`.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `CLAUDE.md` (raiz) | Entrypoint principal. Usa `@AGENTS.md` para importar el contexto comun. |
| `.claude/settings.json` | Hooks, permisos, configuracion del harness |
| `.claude/agents/*.md` | Sub-agents para skills especificos (uno por workflow/skill) |

## Como Claude Code consume las primitivas

1. Al abrir el repo, Claude Code lee `CLAUDE.md`.
2. `CLAUDE.md` incluye `@AGENTS.md` (sintaxis de importacion de Claude Code).
3. El usuario puede referenciar skills con `/skill-name` (slash commands).
4. Los hooks en `.claude/settings.json` se ejecutan automaticamente en eventos del harness.
5. Los sub-agents en `.claude/agents/` pueden invocarse para tareas especificas.

## Limitaciones conocidas

- Los slash commands requieren definicion manual en `.claude/settings.json` o como skills del harness.
- `@import` solo funciona para archivos del repo, no URLs.
- Los hooks son comandos shell: deben ser rapidos para no bloquear.

## Instalar

```bash
./.ai/adapters/claude-code/install.sh
```

El script crea los archivos en `.claude/agents/` con un sub-agent por skill.

## Hooks recomendados (.claude/settings.json)

Ver `.ai/primitives/hooks/` para los detalles de cada hook.

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
