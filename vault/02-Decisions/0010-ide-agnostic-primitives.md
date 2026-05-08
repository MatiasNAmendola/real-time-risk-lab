---
adr: "0010"
title: IDE-Agnostic AI Primitives en .ai/
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/ai]
---

# ADR-0010: IDE-Agnostic AI Primitives en .ai/ Directory

## Estado

Aceptado el 2026-05-07.

## Contexto

AI coding assistants used en este repository: Claude Code (primary), Cursor, GitHub Copilot, Zed AI. Each has its own configuration format: `CLAUDE.md`, `.cursorrules`, `.github/copilot-instructions.md`, `.zed/settings.json`. Maintaining context separately para each IDE creates drift — un project convention added un `CLAUDE.md` must be manually replicated un `.cursorrules`, etc.

The más substantive concern: la repository es un technical-leadership-level technical exploration artifact. A reviewer reading la repository should see que AI tooling has been thought through a la team level, no just configured para personal productivity en one IDE. A `.ai/` directory con primitives, adapters, y agent automation scripts communicates deliberate systems thinking.

ADR-0024 covers la broader `.ai/` directory rationale. Este ADR covers la specific decision un use un primitives-plus-adapters model en vez de per-IDE configuration files directly.

## Decisión

Create `.ai/primitives/` como la canonical source de project context (conventions, domain vocabulary, agent personas, constraint catalogs). Create `.ai/adapters/` para per-IDE configuration files que reference o derive desde la primitives. `AGENTS.md` a repo root follows Claude Code's convention para agentic workflow entry points.

## Alternativas consideradas

### Opción A: .ai/ con primitives y per-IDE adapters (elegida)
- **Ventajas**: Single source de truth — changing un convention requires one edit en `primitives/`; adapters son thin wrappers o generated files; `AGENTS.md` es recognized por Claude Code y compatible tools; demonstrates awareness de la multi-IDE AI tooling landscape; `.ai/scripts/skill-router.py` shows tooling built en top de la primitives.
- **Desventajas**: Requires understanding la two-layer structure; algunos IDEs don't support custom config paths (GitHub Copilot en VS Code uses `.github/copilot-instructions.md`, no configurable); adapter maintenance es needed when IDE config formats evolve.
- **Por qué se eligió**: La primitives layer es la correct abstraction para un team-level AI configuration approach. For un single developer, it es overhead; para un team, it es la only scalable approach.

### Opción B: Per-IDE configuration files only, no central primitives
- **Ventajas**: Standard; each IDE user configures their tool directly; no learning curve para la two-layer model.
- **Desventajas**: Drift es inevitable a través de IDE configs; adding un new IDE requires translating context manually; no machine-readable representation de project conventions para agent tooling.
- **Por qué no**: At team scale, per-IDE drift es un real maintenance burden. As un señal de diseño, per-IDE configs demonstrate personal productivity; primitives demonstrate team-level thinking.

### Opción C: CLAUDE.md only a repository root
- **Ventajas**: Claude Code's native convention; widely recognized; zero extra structure.
- **Desventajas**: Not useful para Cursor o GitHub Copilot; does no communicate multi-IDE awareness; CLAUDE.md grows unbounded sin un modular organization.
- **Por qué no**: La exploration context requires demonstrating la multi-IDE landscape awareness. CLAUDE.md alone es insufficient.

### Opción D: Shared context via README.md conventions section
- **Ventajas**: Visible un any developer opening la repository; no special tooling required.
- **Desventajas**: README conventions son no machine-readable por AI tools; each AI assistant must be explicitly pointed un la README; no structured format para skills, personas, o constraints.
- **Por qué no**: README conventions require per-session manual loading into AI context. La `.ai/` structure provides automatic loading via IDE configuration.

## Consecuencias

### Positivo
- Project domain vocabulary (`FraudRule`, `LatencyBudget`, `DecisionEvaluated`) es available un todos AI assistants via adapters.
- `skill-router.py` demonstrates agentic tooling built en top de la primitives.
- New IDE support = new adapter file, no new content.

### Negativo
- Two-layer indirection (primitives → adapters) adds complexity.
- Some IDE adapter files must be maintained manually when la IDE's config format changes.

### Mitigaciones
- `README.md` en `.ai/` explains la structure.
- Adapters son kept minimal — la mayoría de content stays en `primitives/`.

## Validación

- `.ai/primitives/` contains project context files.
- `CLAUDE.md` o `.ai/adapters/claude-code/` references primitives.
- `.ai/scripts/skill-router.py` routes test prompts correctly.

## Relacionado

- [[0024-ai-directory]]
- [[0025-skill-router-hybrid-scoring]]
- [[0011-engram-mcp-memory]]
- [[0034-doc-driven-vault-structure]]
