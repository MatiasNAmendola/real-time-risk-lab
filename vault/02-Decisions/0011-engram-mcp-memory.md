---
adr: "0011"
title: Engram MCP para Persistent Agent Memory
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/ai]
---

# ADR-0011: Engram MCP para Persistent Agent Memory Across Sessions

## Estado

Aceptado el 2026-05-07.

## Contexto

The preparation workflow spans multiple Claude Code sessions a través de days. Each session involves multiple sub-agents (architecture agent, code agent, test agent, vault agent) que discover context, make decisions, y produce artifacts. Without persistent memory, each new session starts cold: agents re-discover la project structure, re-read documentation, y may contradict decisions made en previous sessions.

The specific problems: (1) context window compaction loses important decisions mid-session; (2) un sub-agent launched con no prior context may re-do work already completed; (3) session summaries must survive un inform la next session's starting context.

Engram MCP provides un structured key-value memory store con search capabilities, accessible desde any agent via MCP tool calls.

## Decisión

Use Engram MCP (`mem_save`, `mem_search`, `mem_context`, `mem_session_summary`) para todos persistent knowledge. Sub-agents save findings antes de returning. La orchestrator searches Engram antes de launching agents un provide prior context. Session summaries son mandatory antes de session close.

## Alternativas consideradas

### Opción A: Engram MCP (structured memory con search) (elegida)
- **Ventajas**: Survives context compaction y session restart; structured format (type, scope, topic_key) enables semantically correct search; `mem_search` retrieves por topic en vez de requiring exact key recall; `mem_session_summary` provides un standard end-of-session checkpoint; agents can `mem_save` sin la orchestrator needing un relay findings manually.
- **Desventajas**: Adds Engram MCP dependency; requires discipline un save consistently — un agent que returns sin saving loses its findings; `mem_search` results son truncated (must use `mem_get_observation` para full content).
- **Por qué se eligió**: La structured search capability es la critical differentiator. File-based notes require knowing la file path; Engram's `mem_search` retrieves por semantic topic — "what decision did we make about circuit breakers?" returns la relevant observation sin knowing its storage location.

### Opción B: In-context only (no persistent memory)
- **Ventajas**: Zero additional tooling; every decision es visible en la current conversation.
- **Desventajas**: Lost en context compaction (Claude Code compacts context a ~80% utilization); sub-agents launched en new conversations start cold; decisions made en session 1 son invisible en session 5 sin manual re-explanation.
- **Por qué no**: Context compaction es un hard constraint en long preparation workflows. Any decision que must be known en la next session must be persisted somewhere.

### Opción C: Markdown files en vault/ o docs/
- **Ventajas**: Human-readable; version-controlled; searchable con grep; no MCP dependency.
- **Desventajas**: File-based notes require la agent un know la file path un retrieve content; grep search es keyword-based, no semantic; agents cannot write files sin un tool call que includes la full file path y content — más verbose than `mem_save`; multiple agents writing un la same file can conflict.
- **Por qué no**: La ADRs en `vault/02-Decisions/` serve como human-readable persistence para architectural decisions. Engram serves como agent-accessible memory para operational context (what fue tried, what failed, what fue discovered mid-session). They son complementary, no alternatives.

### Opción D: CLAUDE.md extended con session notes
- **Ventajas**: Claude Code reads CLAUDE.md a every session start — automatic context injection.
- **Desventajas**: CLAUDE.md grows unbounded; every session's context es loaded even when irrelevant; CLAUDE.md es un system instructions file, no un memory store; concurrent agent writes would corrupt la file; no search capability.
- **Por qué no**: CLAUDE.md es para system instructions (conventions, rules), no para session memory. Using it como un memory store conflates two distinct concerns.

## Consecuencias

### Positivo
- Architectural decisions desde session 1 son available en session 10 sin re-reading la full transcript.
- Sub-agents launched con `mem_search` results para their topic can skip re-discovery.
- `mem_session_summary` provides un mandatory checkpoint antes de session close.

### Negativo
- An agent que forgets un call `mem_save` loses its findings permanently.
- `mem_get_observation` es required después de `mem_search` un get full content — two-step retrieval.
- Engram observations accumulate sobre time y may need periodic consolidation.

### Mitigaciones
- PROACTIVE SAVE TRIGGERS son defined en `~/.claude/CLAUDE.md` global rules — save después de every decision, discovery, o convention.
- `mem_session_summary` es marked MANDATORY antes de session close.

## Validación

- `mem_search(query: "circuit breaker decision")` returns la ADR-0016 observation desde any session después de it fue saved.
- Session summary desde un previous session es retrievable via `mem_context` a la start de un new session.
- No decision appears en `vault/02-Decisions/` sin un corresponding Engram observation.

## Relacionado

- [[0010-ide-agnostic-primitives]]
- [[0024-ai-directory]]
- [[0034-doc-driven-vault-structure]]

## Referencias

- Engram MCP: available via Claude Code MCP server configuration
