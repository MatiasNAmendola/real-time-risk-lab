# Adapter: Codex (OpenAI)

OpenAI Codex CLI lee `AGENTS.md` en la raiz del repo. Codex origino la convencion `AGENTS.md` que fue luego adoptada por otros tools.

## Archivos que usa este adapter

| Archivo | Proposito |
|---|---|
| `AGENTS.md` (root) | Path canonico. Codex lo lee automaticamente. |
| `.codex/AGENTS.md` | Symlink a `../AGENTS.md` (no duplicacion — mismo contenido) |
| `.codex/config.toml` | Config MCP por proyecto (solo cargada en proyectos "trusted") |
| `~/.codex/config.toml` | Config MCP global |

## Cascade lookup order (por directorio)

Codex busca instrucciones siguiendo esta precedencia:

```
AGENTS.override.md  -> mayor precedencia (overrides locales)
AGENTS.md           -> path canonico
TEAM_GUIDE.md       -> fallback de equipo
.agents.md          -> fallback oculto
```

El lookup sube el arbol de directorios desde el directorio actual.
`AGENTS.override.md` es util para overrides locales que no se commitean al repo.

## Porque .codex/AGENTS.md es un symlink

El path canonico es `AGENTS.md` en root, no `.codex/AGENTS.md`.
El symlink existe para compatibilidad si Codex busca en `.codex/` en alguna version,
pero el archivo real que Codex lee es el de la raiz.

## Configuracion MCP (opcional)

```toml
# .codex/config.toml (proyecto trusted)
[mcp_servers.my-server]
command = "node path/to/server.js"

[mcp_servers.remote]
url = "https://my-mcp-server.example.com/mcp"

model = "o3"
approval_mode = "auto"
```

Soporta: stdio (local) y streaming HTTP (remoto).

## Limitaciones conocidas

- `AGENTS.md` en root puede ser leido por otros tools (Antigravity, opencode).
- `.codex/config.toml` solo se carga en proyectos marcados como "trusted" (seguridad).
- No hay equivalente a hooks de Claude Code.

## Instalar

```bash
./.ai/adapters/codex/install.sh
```

## Documentacion oficial

- https://developers.openai.com/codex/guides/agents-md
- https://developers.openai.com/codex/config-reference
- https://developers.openai.com/codex/mcp
