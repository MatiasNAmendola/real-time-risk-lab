# Adapter: opencode

opencode is an open-source terminal UI coding agent written in Go.
Project config: `opencode.json` at the repository root, with higher precedence than global config.

> Confidence: medium. Main config is documented; internal `agents/` and `skills/` directories under `~/.config/opencode/` are not fully specified.

## Files used by this adapter

| File | Purpose |
|---|---|
| `opencode.json` (root) | Project config, higher precedence than global config. |
| `~/.config/opencode/opencode.json` | Global config, lower precedence. |

## `opencode.json` format

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

## How opencode consumes primitives

1. opencode loads `opencode.json` when started in the repo directory.
2. The `instructions` field contains project context.
3. The agent can read additional files with its file tools.
4. MCP servers are configured under `mcp.servers`.

## Global subdirectories

`~/.config/opencode/` may contain `agents/`, `commands/`, `modes/`, `plugins/`, `skills/`, `tools/`, and `themes/`.
The internal structure of `agents/` and `skills/` is not fully documented in the verified sources.
These are global system directories, not project directories.

## Do not confuse with

Charmbracelet Crush. It is a different terminal AI tool.
opencode is `github.com/opencode-ai/opencode`.

## Known limitations

- Internal global `agents/` and `skills/` structure is not fully documented.
- Hot reload is not clearly documented and may require restart.

## Install

```bash
./.ai/adapters/opencode/install.sh
```

## Official documentation

- https://opencode.ai/docs/config/
- https://opencode.ai/docs/mcp-servers/
- https://github.com/opencode-ai/opencode
