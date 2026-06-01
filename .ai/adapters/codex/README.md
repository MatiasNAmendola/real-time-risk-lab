# Adapter: Codex (OpenAI)

OpenAI Codex CLI reads `AGENTS.md` at the repository root. Codex originated the `AGENTS.md` convention that other tools later adopted.

## Files used by this adapter

| File | Purpose |
|---|---|
| `AGENTS.md` (root) | Canonical path. Codex reads it automatically. |
| `.codex/AGENTS.md` | Symlink to `../AGENTS.md` to avoid duplicated instructions. |
| `.codex/config.toml` | Project MCP config, loaded only for trusted projects. |
| `~/.codex/config.toml` | Global MCP config. |

## Cascade lookup order

Codex resolves instructions with this precedence:

```text
AGENTS.override.md  -> highest priority local overrides
AGENTS.md           -> canonical path
TEAM_GUIDE.md       -> team fallback
.agents.md          -> hidden fallback
```

Lookup walks upward from the current working directory.
Use `AGENTS.override.md` only for local, non-committed overrides.

## Why `.codex/AGENTS.md` is a symlink

The canonical path is root `AGENTS.md`, not `.codex/AGENTS.md`.
The symlink exists only as a compatibility fallback if a Codex version checks `.codex/`.
The real source of truth remains the root file.

## Optional MCP config

```toml
# .codex/config.toml (trusted project)
[mcp_servers.my-server]
command = "node path/to/server.js"

[mcp_servers.remote]
url = "https://my-mcp-server.example.com/mcp"

model = "o3"
approval_mode = "auto"
```

Supported transports: local stdio and remote streaming HTTP.

## Known limitations

- Root `AGENTS.md` can also be read by other tools such as Antigravity and opencode.
- `.codex/config.toml` is loaded only for trusted projects.
- Codex has no direct equivalent to Claude Code hooks.

## Install

```bash
./.ai/adapters/codex/install.sh
```

## Official documentation

- https://developers.openai.com/codex/guides/agents-md
- https://developers.openai.com/codex/config-reference
- https://developers.openai.com/codex/mcp
