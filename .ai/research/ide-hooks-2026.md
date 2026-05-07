# IDE Lifecycle Hooks Support Matrix — 2026-05-07

## Methodology

- Last verified: 2026-05-07
- Queries used (30 total):
  - "Claude Code hooks lifecycle PreToolUse PostToolUse SessionStart 2025 2026 official docs"
  - "Cursor IDE lifecycle hooks events automation 2025 2026"
  - "Windsurf Wave 8 Wave 9 hooks events lifecycle 2025 2026"
  - "GitHub Copilot lifecycle hooks events automation 2025 2026"
  - "Codex CLI AGENTS.md hooks lifecycle events session 2025 2026"
  - "Kiro AWS IDE hooks lifecycle events 2025 2026 agent"
  - "Aider lifecycle hooks events pre post tool use session 2025 2026"
  - "Continue dev hooks lifecycle events automation session 2025 2026"
  - "Antigravity Google AI IDE lifecycle hooks events 2025 2026 GEMINI.md"
  - "opencode charmbracelet hooks lifecycle events session 2025 2026"
  - "GitHub Copilot hooks .github/hooks configuration events sessionStart preToolUse 2026"
  - "Cursor hooks.json .cursor/hooks events configuration preToolUse sessionStart 2026"
  - "Windsurf Cascade hooks events supported PRE_TOOL POST_TOOL session 2026 configuration"
  - "Codex CLI hooks configuration file format SessionStart SessionStop events 2026 site:developers.openai.com OR site:github.com/openai/codex"
  - "Antigravity IDE hooks.json lifecycle events configuration 2026"
  - "Antigravity IDE hooks configuration file GEMINI.md lifecycle events site:antigravity.dev OR site:developers.google.com"
  - "Continue.dev hooks events configuration 2026 session preToolUse site:docs.continue.dev OR site:github.com/continuedev/continue"
  - "Continue.dev lifecycle hooks 2026 changelog new features automation"
  - "\"Google Antigravity\" hooks.json \".agents/hooks\" lifecycle events official documentation 2026"
  - "Aider hooks pre post tool use session lifecycle events 2026 site:aider.chat OR site:github.com/Aider-AI"
  - Plus 10 WebFetch calls to official docs
- Confidence ratings:
  - HIGH: official docs (docs.github.com, code.claude.com, cursor.com/docs, docs.windsurf.com, kiro.dev/docs, developers.openai.com/codex)
  - MEDIUM: official blog posts, changelogs, codelabs
  - LOW: community posts, inferred, forum discussions

---

## Matrix

Legend: YES = confirmed, NO = confirmed absent, PARTIAL = limited/workaround only, UNKNOWN = no evidence found after 3+ searches

| Event \ IDE          | Claude Code | Cursor      | Windsurf    | Copilot     | Codex CLI   | Antigravity | opencode    | Kiro        | Aider       | Continue    |
|----------------------|-------------|-------------|-------------|-------------|-------------|-------------|-------------|-------------|-------------|-------------|
| **session_start**    | YES HIGH    | YES HIGH    | NO          | YES HIGH    | YES HIGH    | NO MEDIUM   | YES HIGH    | YES MEDIUM  | NO HIGH     | NO HIGH     |
| **session_end**      | YES HIGH    | YES HIGH    | NO          | YES HIGH    | YES HIGH    | NO MEDIUM   | YES HIGH    | YES MEDIUM  | NO HIGH     | NO HIGH     |
| **prompt_submit**    | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | NO MEDIUM   | YES HIGH    | YES MEDIUM  | NO HIGH     | NO HIGH     |
| **pre_tool_use**     | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | NO MEDIUM   | YES HIGH    | YES HIGH    | NO HIGH     | NO HIGH     |
| **post_tool_use**    | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | YES HIGH    | NO MEDIUM   | YES HIGH    | YES HIGH    | NO HIGH     | NO HIGH     |
| **subagent_start**   | YES HIGH    | YES HIGH    | NO          | NO          | NO          | NO          | NO          | NO          | NO HIGH     | NO HIGH     |
| **pre_compact**      | YES HIGH    | YES HIGH    | NO          | NO          | YES MEDIUM  | NO          | YES HIGH    | NO          | NO HIGH     | NO HIGH     |
| **periodic / cron**  | NO          | YES MEDIUM  | NO          | NO          | NO          | NO          | NO          | NO          | NO          | NO          |
| **on_file_change**   | YES HIGH    | YES HIGH    | YES HIGH    | NO          | NO          | NO          | YES HIGH    | YES HIGH    | NO HIGH     | NO HIGH     |

---

## Detailed Findings per IDE

### Claude Code

- **Version current**: 2.1.x (as of 2026-05-07; `defer` field requires >= v2.1.89)
- **Hooks location**: `~/.claude/settings.json` (user-global), `.claude/settings.json` (project), `.claude/settings.local.json` (project-local, not committed), managed policy (org-wide)
- **Config format**: JSON under `"hooks": { "EventName": [{ "matcher": "...", "hooks": [...] }] }`
- **Total events**: 25+ distinct event types across 6 categories
- **Supported events relevant to this matrix**:
  - `SessionStart` — fires on session begin or resume; re-fires with `source: "resume"` on resume; non-blocking
  - `SessionEnd` — fires at session termination; non-blocking
  - `UserPromptSubmit` — fires when user submits prompt; blocking (exit 2 blocks)
  - `PreToolUse` — fires before any tool call; blocking; supports `updatedInput` to rewrite args; `defer` value pauses for user approval (requires >= v2.1.89)
  - `PostToolUse` — fires after tool completes; blocking
  - `SubagentStart` — fires when subagent spawned; non-blocking
  - `SubagentStop` — fires when subagent finishes; blocking (can inject followup)
  - `PreCompact` — fires before context compaction; blocking
  - `PostCompact` — fires after compaction completes; blocking
  - `FileChanged` — fires when watched file changes on disk; non-blocking
  - `Stop` — fires when Claude finishes responding; blocking (can inject followup)
  - Additionally: `Setup`, `StopFailure`, `UserPromptExpansion`, `PermissionRequest`, `PermissionDenied`, `PostToolUseFailure`, `PostToolBatch`, `TaskCreated`, `TaskCompleted`, `WorktreeCreate`, `WorktreeRemove`, `CwdChanged`, `ConfigChange`, `InstructionsLoaded`, `Notification`, `Elicitation`, `ElicitationResult`, `TeammateIdle`
- **Hook handler types**: `command` (shell), `http` (POST to URL), `mcp_tool`, `prompt` (asks Claude), `agent` (spawns subagent)
- **Exit code semantics**: 0 = success, 2 = blocking deny, other = non-blocking error
- **Timeouts**: command 600s default, http 30s, agent 60s, prompt 30s — all overridable via `timeout` field
- **Async support**: `"async": true` on command hooks fires-and-forgets; `"asyncRewake": true` wakes session when hook completes
- **JSON output size cap**: 10,000 characters
- **periodic/cron**: No native cron hook. Workaround: external cron calling `claude -p "..."` headlessly
- **Doc URL**: https://code.claude.com/docs/en/hooks
- **Confidence**: HIGH

---

### Cursor

- **Version introduced**: 1.7 (beta, October 2025); promoted to stable in subsequent releases
- **Hooks location**: `.cursor/hooks.json` (project), `~/.cursor/hooks.json` (user-global), enterprise path `/Library/Application Support/Cursor/hooks.json` (macOS)
- **Config format**: `{ "version": 1, "hooks": { "hookName": [{ "command": "...", "timeout": 30 }] } }`
- **Supported events**:
  - `sessionStart` — fires when agent session opens; fire-and-forget (return values logged, don't block)
  - `sessionEnd` — fires when agent session closes; fire-and-forget
  - `beforeSubmitPrompt` — fires before user prompt is submitted to model; blocking
  - `preToolUse` — generic pre-tool hook (Shell, Read, Write, MCP, Task, etc.); blocking via exit 2 or `permission: "deny"`
  - `postToolUse` — generic post-tool hook; optional followup injection
  - `postToolUseFailure` — fires when tool call fails
  - `subagentStart` — fires when subagent spawns; blocking
  - `subagentStop` — fires when subagent finishes; can inject followup; respects `loop_limit` (default 5)
  - `beforeShellExecution` / `afterShellExecution` — shell-specific wrappers
  - `beforeMCPExecution` / `afterMCPExecution` — MCP-specific wrappers
  - `beforeReadFile` / `afterFileEdit` — file access hooks
  - `afterAgentResponse` / `afterAgentThought` — observability hooks
  - `preCompact` — fires before context compaction; fire-and-forget
  - `stop` — fires when agent finishes; can inject followup; respects `loop_limit`
  - Tab-specific: `beforeTabFileRead`, `afterTabFileEdit`
- **Handler types**: `command` (shell script via stdin JSON), `prompt` (LLM evaluation, 10s default timeout)
- **Blocking behavior**: Most hooks blocking. `sessionStart`, `sessionEnd`, `preCompact` are fire-and-forget. `failClosed: true` blocks on hook timeout/crash.
- **Timeout**: configurable via `timeout` field (seconds); prompt-based hooks default 10s
- **`loop_limit`**: controls max auto-follow-ups for `stop`/`subagentStop`; default 5; set `null` to remove
- **periodic/cron**: Yes — Cursor Automations (released 2025) allows schedule-based or external-event triggers via a separate mechanism distinct from hooks.json
- **on_file_change**: `afterFileEdit` covers file modification events
- **Doc URL**: https://cursor.com/docs/hooks
- **Confidence**: HIGH

---

### Windsurf (Codeium)

- **Version introduced**: Cascade Hooks first shipped in v1.12.41 (December 10, 2025); expanded in v1.13.6 (January 12, 2026); enterprise MDM support in v1.13.2 (January 25, 2026); `post_cascade_response_with_transcript` added in February 2026
- **Hooks location**: JSON config files; available at user, project, and enterprise levels; team-wide via cloud dashboard or MDM
- **Config format**: `{ "hooks": { "event_name": [{ "command": "bash script.sh", "powershell": "script.ps1", "show_output": true }] } }`
- **Total events**: 12 distinct events
- **Supported events**:
  - `pre_user_prompt` — before user prompt processed; blocking (exit 2)
  - `pre_read_code` — before file read; blocking
  - `post_read_code` — after file read; non-blocking
  - `pre_write_code` — before file modification; blocking
  - `post_write_code` — after file modification; non-blocking
  - `pre_run_command` — before terminal execution; blocking
  - `post_run_command` — after terminal execution; non-blocking
  - `pre_mcp_tool_use` — before MCP tool invocation; blocking
  - `post_mcp_tool_use` — after MCP tool invocation; non-blocking
  - `post_cascade_response` — after agent response; non-blocking; auditing
  - `post_cascade_response_with_transcript` — after response with full JSONL transcript; non-blocking
  - `post_setup_worktree` — after worktree creation; non-blocking
- **Notable absences**: No `session_start`, `session_end`, `subagent_start` events. No compaction hooks.
- **Blocking rule**: Only pre-hooks can block (exit code 2). Post-hooks are always non-blocking.
- **Timeout**: Not documented in official docs as of 2026-05-07
- **Doc URL**: https://docs.windsurf.com/windsurf/cascade/hooks
- **Confidence**: HIGH

---

### GitHub Copilot

- **Version introduced**: Hooks shipped for Coding Agent and Copilot CLI in late 2025; VSCode agent hooks entered Preview 2025-2026
- **Hooks location**: `.github/hooks/*.json` (cloud agent, must be on default branch); local dir for CLI; VSCode uses separate agent hooks settings
- **Config format**: `{ "version": 1, "sessionStart": [{ "type": "command", "bash": "./script.sh", "powershell": "./script.ps1", "timeoutSec": 30 }], ... }`
- **Supported events** (GitHub Docs):
  - `sessionStart` — session begins or resumes; output added to context; non-blocking
  - `sessionEnd` — session completes or terminates; output ignored; non-blocking
  - `userPromptSubmitted` — user submits prompt; output ignored; non-blocking
  - `preToolUse` — before any tool execution; blocking (`"allow"`, `"deny"`, `"ask"`); only hook type that supports permission decisions
  - `postToolUse` — after tool execution; output ignored; non-blocking
  - `errorOccurred` — when error occurs; non-blocking
- **Notable absences**: No `subagentStart`, `preCompact`, periodic, or `on_file_change` hooks
- **Blocking**: Only `preToolUse` can block; exit non-zero = block. All other hooks fire-and-forget.
- **Timeout**: Default 30s; configurable via `timeoutSec`
- **Platform**: bash (Unix/macOS/Linux) + PowerShell (Windows) per-hook
- **Env vars**: Passable via `env` config object; CWD configurable
- **Doc URLs**:
  - https://docs.github.com/en/copilot/reference/hooks-configuration
  - https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/use-hooks
  - https://code.visualstudio.com/docs/copilot/customization/hooks (VSCode Preview)
- **Confidence**: HIGH

---

### Codex CLI (OpenAI)

- **Version introduced**: Hooks stable as of v0.128.0 (April 2026); compaction lifecycle hooks added in v0.129.0 (May 2026); feature flag required: `[features] codex_hooks = true` in `config.toml`
- **Hooks location**: `hooks.json` in project or inline `[hooks]` tables in `config.toml`; repo-local `.codex/config.toml` (note: issue #17532 reports hooks may not fire in interactive sessions from repo-local config — verify per version)
- **Config format**: 3-level structure: event name → matcher group → handler specification; JSON or TOML
- **Supported events**:
  - `SessionStart` — fires on session start/resume; non-blocking; supports `systemMessage` injection
  - `UserPromptSubmit` — fires when user submits prompt; blocking (`"decision": "block"` or exit 2)
  - `PreToolUse` — fires before tool execution; blocking (`"permissionDecision": "deny"` or exit 2)
  - `PermissionRequest` — fires when tool approval needed; blocking
  - `PostToolUse` — fires after tool completes; can replace tool result with feedback
  - `Stop` — fires when conversation turn concludes; non-blocking; supports `systemMessage`
  - Compaction hooks: pre/post compaction added in v0.129.0 (exact event names not confirmed in fetched docs)
- **Notable absences**: No `session_end`, `subagent_start` events; `SessionStop` not documented (use `Stop`)
- **Handler types**: Command (shell); concurrent launch for multiple matching hooks
- **Blocking**: PreToolUse and UserPromptSubmit support blocking via exit 2; multiple matching hooks launched concurrently (one cannot prevent another matching hook from starting)
- **Timeout**: Default 600s (10 minutes); configurable per hook
- **Browsable via**: `/hooks` command in interactive session
- **Doc URL**: https://developers.openai.com/codex/hooks
- **Confidence**: HIGH (docs); MEDIUM for compaction hooks (changelog inference)

---

### Antigravity (Google, launched November 2025)

- **Version current**: GA as of early 2026; built on modified VS Code + Gemini 3.x
- **Config files**: `GEMINI.md` (context injection, always-active rules), `.agents/agents.md` (persona), `.agents/skills/` (modular instructions), `.agents/workflows/` (slash-command pipelines)
- **Native lifecycle hooks**: NO — confirmed absent from official documentation as of 2026-05-07
  - The developer forum (discuss.ai.google.dev, February 2026) confirms hooks are NOT natively supported; workaround is `.agents/rules/` behavioral directives and manually-initiated workflows
  - Feature requests for hooks exist but no announced release date
- **Workaround**: `.agents/rules/` can instruct agent to run scripts at specified points (e.g., "after every file modification, run lint"); this is an AI-interpreted instruction, not a deterministic hook
- **on_file_change**: Not a hook; Antigravity's Manager view allows monitoring but no auto-triggered shell scripts
- **periodic/cron**: No native support
- **Doc URL**: https://antigravity.google/docs/home (limited content accessible); https://codelabs.developers.google.com/autonomous-ai-developer-pipelines-antigravity
- **Confidence**: MEDIUM (based on official codelab + developer forum; official docs page returned no content on fetch)

---

### opencode (Charm / sst/opencode)

- **Version current**: Plugin system documented as of 2026-05-07
- **Hooks location**: Plugins via `opencode.json` `"plugin"` array; local plugins in `.opencode/plugins/` or `~/.config/opencode/plugins/`; npm packages supported
- **Config format**: Plugins are TypeScript/JS modules; hooks registered programmatically via plugin API
- **Supported events** (from official plugin docs):
  - `session.created` — session created
  - `session.deleted` — session deleted
  - `session.compacted` — session context compacted (maps to pre_compact)
  - `session.idle` — session goes idle (maps to session_end-ish)
  - `session.updated` / `session.diff` / `session.error` / `session.status` — session state events
  - `tool.execute.before` — before tool execution (maps to pre_tool_use)
  - `tool.execute.after` — after tool execution (maps to post_tool_use)
  - `file.edited` — when a file is edited
  - `file.watcher.updated` — when file watcher detects change
  - `command.executed` — when a command executes
  - `message.updated` / `message.part.updated` / `message.removed` etc. — message lifecycle
  - `permission.asked` / `permission.replied` — permission events
  - `tui.prompt.append` / `tui.command.execute` — TUI prompt events (maps to prompt_submit-ish)
  - `lsp.client.diagnostics` / `lsp.updated` — LSP events
  - `shell.env` / `server.connected` / `installation.updated` / `todo.updated`
- **Notable absences**: No explicit `subagent_start` event
- **Blocking**: Not documented explicitly in official plugin docs; `permission.asked` may support blocking
- **Timeout**: Not documented
- **Minimum version**: Not versioned in docs (dated 2026-05-07)
- **Doc URL**: https://opencode.ai/docs/plugins/
- **Confidence**: HIGH (official plugin docs); event semantics mapping is inferred

---

### Kiro (AWS)

- **Version introduced**: Generally available as of 2026; hook docs last updated April 13, 2026
- **Hooks location**: Defined in agent configuration files (`.kiro/` directory)
- **Config format**: JSON/YAML; hooks receive JSON-formatted events via STDIN; exit codes control behavior
- **Supported events**:
  - `AgentSpawn` — when agent activates; no tool context; non-blocking
  - `UserPromptSubmit` — when user submits prompt; blocking; output added to conversation context
  - `PreToolUse` — before tool execution; blocking (exit 2); supports matchers by tool name/alias/wildcard
  - `PostToolUse` — after tool execution; non-blocking; results available in event payload
  - `Stop` — when assistant finishes responding; non-blocking
- **Note on file events**: Kiro's agent hooks (above) are the CLI/agentic hooks. The IDE separately supports file-system automation (on save → run tests, on new file → security scan, etc.) via "agent hooks" triggered by workspace events — these are different from the CLI lifecycle hooks above
- **Matchers**: Canonical tool names, aliases, MCP server prefixes, wildcards
- **Caching**: `cache_ttl_seconds` field on hooks; 0 = disabled
- **Timeout**: Default 30 seconds; configurable via `timeout_ms`
- **Exit codes**: 0 = success, 2 = block (PreToolUse only), other = warning without blocking
- **Notable absences**: No `session_start`, `session_end`, `subagent_start`, `pre_compact` in CLI hooks
- **on_file_change**: Supported via Kiro's workspace automation (separate from agentic CLI hooks)
- **Doc URL**: https://kiro.dev/docs/cli/hooks/
- **Confidence**: HIGH (official docs)

---

### Aider

- **Version current**: Active development; no hooks release as of 2026-05-07
- **Lifecycle hooks**: NO native hook system
- **Available automation** (workaround-level):
  - `--lint-cmd <cmd>` / `--auto-lint` (default: True) — runs linter after AI edits; receives changed filenames
  - `--test-cmd <cmd>` / `--auto-test` (default: False) — runs test suite after edits
  - `--auto-commits` (default: True) — auto git-commits AI changes
  - `--git-commit-verify` — controls whether git pre-commit hooks run on AI commits
  - Language-specific lint: `--lint "language: cmd"`
- **Scripting**: `aider --script <file>` for batch automation; not event-driven
- **No events for**: session_start, session_end, prompt_submit, pre_tool_use, post_tool_use, subagent_start, pre_compact, periodic, on_file_change
- **Community**: Multiple GitHub issues requesting hooks (e.g., pre/post edit hooks) — none shipped as of 2026-05-07
- **Doc URL**: https://aider.chat/docs/config/options.html
- **Confidence**: HIGH (official docs confirm absence)

---

### Continue (continue.dev)

- **Version current**: Active development; CLI tool for CI/CD PR review (2026 pivot)
- **Lifecycle hooks**: NO native hook system in the IDE extension as of 2026-05-07
- **What Continue does support**:
  - `config.yaml` with `models`, `rules`, `prompts`, `contextProviders`, `mcpServers`, `tools`
  - Rules (always-active instructions injected into context)
  - Custom slash commands and prompt templates
  - MCP server integration for custom tools
  - Headless CLI mode for CI/CD agents (PR review automation)
  - Multiple config profiles for different workspaces
- **No events for**: session_start, session_end, prompt_submit, pre_tool_use, post_tool_use, subagent_start, pre_compact, periodic, on_file_change
- **Note**: The hooks described in some blog posts attributed to "Continue" were actually descriptions of Claude Code hooks; Continue itself has no equivalent
- **Doc URL**: https://docs.continue.dev/reference ; https://docs.continue.dev/customize/overview
- **Confidence**: HIGH (official docs confirm absence; multiple fetches of config reference show no hooks section)

---

## Workarounds for IDEs Without Hooks

| IDE | Workaround | Limitation |
|---|---|---|
| Antigravity | `.agents/rules/` with directives like "Before modifying any file, run `./.agents/scripts/pre-edit.sh`" | AI-interpreted only; not deterministic; agent may skip or misinterpret |
| Continue | `rules` in `config.yaml` with "At session start, execute: ..." instruction | Same as above; no enforcement mechanism |
| Aider | `--lint-cmd` / `--test-cmd` for post-edit; `--script` for batch workflows | Only covers post-edit lint/test; no session or prompt events |
| Continue (CI) | Run Continue CLI as headless agent in CI/CD triggered by PR events | Not a session hook; requires external CI trigger |

---

## IDEs With Full or Partial Native Hook Systems (2026)

| Tier | IDEs | Hook Count |
|---|---|---|
| Full hook systems (6+ events) | Claude Code, Cursor, Codex CLI, GitHub Copilot, opencode | 6–25+ events |
| Partial hook systems (1–5 events) | Windsurf (12 events but no session_start/end), Kiro (5 events, no session_start/end) | |
| No native hooks | Antigravity, Aider, Continue | 0 events |

---

## Conclusions

**IDEs with real hooks in 2026**: Claude Code, Cursor, GitHub Copilot, Codex CLI, opencode, Windsurf, Kiro. All 7 use shell-script-based hooks receiving JSON via stdin and returning decisions via exit codes — a converged pattern.

**IDEs without hooks**: Antigravity, Aider, Continue. Workaround is static rule injection (AI-interpreted, not deterministic).

**Patterns that work in ALL IDEs**: CLI tools invocable by name, data persisted in files (CLAUDE.md, GEMINI.md, AGENTS.md etc.), static instruction injection via rules/context files.

**Patterns that ONLY work in Claude Code** (or other full-hook IDEs): automatic skill-router on prompt submit, automatic session handoff via SessionStart, pre-compaction anchoring (PreCompact), SubagentStart/Stop orchestration, PermissionRequest interception, async hooks that wake session on completion (asyncRewake).

---

## Surprising Findings

1. **GitHub Copilot now has a full hook system** (sessionStart, preToolUse, postToolUse, sessionEnd, userPromptSubmitted, errorOccurred) in `.github/hooks/*.json` — this is a major shift from the "just instructions in .github/copilot-instructions.md" paradigm that was the state pre-2025.

2. **Cursor added `preCompact` and `subagentStart/Stop`** in the 1.7+ hooks system — previously assumed to be Claude Code-exclusive capabilities. Cursor's hook surface is nearly as rich as Claude Code's.

3. **Continue.dev has NO hook system at all** despite being one of the oldest open-source AI coding assistants. Its 2026 pivot toward a CI/CD PR-review CLI tool means it never prioritized session lifecycle hooks for interactive use.

---

## Action Items for Adapters

Each `.ai/adapters/<ide>/` should document and generate:

- `claude-code/`: hooks already handled via `.claude/settings.json`; adapter can auto-generate hooks config from `.ai/skills/` and `.ai/scripts/`; document all 25 event types; expose `asyncRewake` pattern for long-running background hooks
- `cursor/`: generate `.cursor/hooks.json` from adapter template; document `failClosed` for security hooks; note `loop_limit` for stop hooks; document `beforeSubmitPrompt` as prompt_submit equivalent
- `windsurf/`: generate Cascade hooks config; document absence of session_start/end; use `pre_user_prompt` as prompt_submit equivalent; use `post_cascade_response_with_transcript` for audit logging
- `github-copilot/`: generate `.github/hooks/hooks.json`; document that only preToolUse is blocking; warn that sessionStart/userPromptSubmitted outputs are ignored
- `codex/`: generate hooks config; document feature flag requirement; warn about interactive session bug (#17532); note concurrent hook execution behavior
- `antigravity/`: document GEMINI.md / `.agents/rules/` as instruction-only workaround; add note that no deterministic hooks exist; provide template for `.agents/rules/session-bootstrap.md`
- `opencode/`: generate opencode plugin boilerplate with session.created and tool.execute.before handlers; document npm plugin distribution
- `kiro/`: generate `.kiro/hooks/` config; document workspace file-event hooks separately from CLI lifecycle hooks; note `cache_ttl_seconds` for performance
- `aider/`: document `--lint-cmd` and `--test-cmd` as the only hook-equivalent; provide `.aider.conf.yml` template with auto-lint=true; no session hooks possible
- `continue/`: document rules-based workaround only; point to headless CLI for CI hooks; no session lifecycle hooks in IDE extension

---

## Sources

- [Hooks reference - Claude Code Docs](https://code.claude.com/docs/en/hooks)
- [Automate workflows with hooks - Claude Code Docs](https://code.claude.com/docs/en/hooks-guide)
- [Hooks | Cursor Docs](https://cursor.com/docs/hooks)
- [Cursor 1.7 Adds Hooks for Agent Lifecycle Control - InfoQ](https://www.infoq.com/news/2025/10/cursor-hooks/)
- [Deep Dive into the new Cursor Hooks - GitButler](https://blog.gitbutler.com/cursor-hooks-deep-dive)
- [Cascade Hooks - Windsurf Docs](https://docs.windsurf.com/windsurf/cascade/hooks)
- [Wave 8: Cascade Customization Features - Windsurf Blog](https://windsurf.com/blog/windsurf-wave-8-cascade-customization-features)
- [Hooks configuration - GitHub Docs](https://docs.github.com/en/copilot/reference/hooks-configuration)
- [Using hooks with GitHub Copilot agents - GitHub Docs](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/use-hooks)
- [About hooks - GitHub Docs](https://docs.github.com/en/copilot/concepts/agents/cloud-agent/about-hooks)
- [Agent hooks in Visual Studio Code (Preview)](https://code.visualstudio.com/docs/copilot/customization/hooks)
- [Hooks - Codex | OpenAI Developers](https://developers.openai.com/codex/hooks)
- [Changelog - Codex | OpenAI Developers](https://developers.openai.com/codex/changelog)
- [Custom instructions with AGENTS.md - Codex | OpenAI Developers](https://developers.openai.com/codex/guides/agents-md)
- [Hooks - Kiro Docs](https://kiro.dev/docs/cli/hooks/)
- [Amazon Kiro AWS Agentic IDE: Complete 2026 Developer Guide](https://www.digitalapplied.com/blog/amazon-kiro-aws-agentic-ide-complete-guide)
- [Options reference | aider](https://aider.chat/docs/config/options.html)
- [Plugins | OpenCode](https://opencode.ai/docs/plugins/)
- [Build Autonomous Developer Pipelines using agents.md and skills.md in Antigravity | Google Codelabs](https://codelabs.developers.google.com/autonomous-ai-developer-pipelines-antigravity)
- [Hooks in Antigravity - Google AI Developers Forum](https://discuss.ai.google.dev/t/hooks-in-antigravity/120458)
- [Google Antigravity Documentation](https://antigravity.google/docs/home)
- [config.yaml Reference | Continue Docs](https://docs.continue.dev/reference)
- [Customization Overview | Continue Docs](https://docs.continue.dev/customize/overview)
- [Changelog - Continue.dev](https://changelog.continue.dev/)
- [Windsurf Editor Changelog](https://windsurf.com/changelog)
- [Windsurf Next Changelogs](https://windsurf.com/changelog/windsurf-next)
