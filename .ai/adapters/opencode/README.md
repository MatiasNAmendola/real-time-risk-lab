# Adapter: opencode

opencode es un CLI TUI open source escrito en Go para coding AI.
Config de proyecto: `opencode.json` en root (mayor precedencia que config global).

> Confianza: MEDIA — config principal (`opencode.json`) bien documentada;
> estructura interna de `agents/`, `skills/` subdirs en `~/.config/opencode/` no completamente especificada.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `opencode.json` (root) | Config del proyecto. Mayor precedencia que global. |
| `~/.config/opencode/opencode.json` | Config global (menor precedencia) |

## Formato de opencode.json

```json
{
  "provider": "anthropic",
  "model": "claude-sonnet-4-6",
  "instructions": "Project-specific instructions here...",
  "mcp": {
    "servers": {
      "filesystem": {
        "command": ["npx", "@modelcontextprotocol/server-filesystem", "/allowed/path"]
      },
      "remote": {
        "url": "https://my-mcp-server.com/mcp"
      }
    }
  },
  "agents": {},
  "permissions": {}
}
```

## Como opencode consume las primitivas

1. opencode carga `opencode.json` al iniciar en el directorio del repo.
2. El campo `instructions` contiene el contexto del proyecto.
3. El agente puede leer archivos adicionales con herramientas de lectura.
4. MCP servers se configuran bajo `mcp.servers`.

## Subdirectorios globales (documentacion incompleta)

`~/.config/opencode/` contiene: `agents/`, `commands/`, `modes/`, `plugins/`, `skills/`, `tools/`, `themes/`.
La estructura interna de `agents/` y `skills/` no esta completamente documentada en las sources verificadas.
No confundir: estos son directorios del sistema global, no del proyecto.

## No confundir con

"Crush" de Charmbracelet — es una herramienta diferente en el mismo espacio de terminal AI.
opencode es `github.com/opencode-ai/opencode` (Go), no el proyecto Charmbracelet.

## Limitaciones conocidas

- Estructura interna de `agents/` y `skills/` en config global no completamente documentada.
- Hot reload no documentado — puede requerir reinicio.

## Instalar

```bash
./.ai/adapters/opencode/install.sh
```

## Documentacion oficial

- https://opencode.ai/docs/config/
- https://opencode.ai/docs/mcp-servers/
- https://github.com/opencode-ai/opencode
