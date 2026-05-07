---
adr: "0024"
title: .ai/ Directory con IDE-Agnostic AI Primitives
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/ai]
---

# ADR-0024: .ai/ Directory con IDE-Agnostic AI Primitives

## Estado

Aceptado el 2026-05-07.

## Contexto

AI coding assistants have become first-class development tools en la industry broadly. Cursor, GitHub Copilot, Claude Code, Zed AI, y Windsurf each have proprietary configuration formats: `.cursorrules`, `.github/copilot-instructions.md`, `CLAUDE.md`, `.zed/settings.json`, `.windsurfrules`. A repository que configures AI assistance para only one IDE creates drift when team members use different tools.

For un exploration repository, este problem es compounded: la repository must work well para la primary author (using Claude Code primarily) y must visually signal un reviewers que AI-assisted development has been thought through a la team level, no just para personal productivity.

The `.ai/` directory es la response: un single source de truth para project context, conventions, agent personas, y workflow definitions, con un thin adapter layer que generates per-IDE configuration desde la primitives.

## Decisión

Create `.ai/` a repository root con la following structure: `primitives/` (project context, conventions, agent roles, constraint catalogs), `adapters/` (generated o manually maintained per-IDE config files), `scripts/` (skill router, context loader, other automation). La root `AGENTS.md` provides agentic workflow instructions para Claude Code y other agent-capable tools.

IDE-specific files (`CLAUDE.md`, `.cursorrules`, etc.) son either generated desde `primitives/` o son thin wrappers que include content desde `primitives/`. La primitives son la canonical source; adapters son derivations.

## Alternativas consideradas

### Opción A: .ai/ con primitives y adapters (elegida)
- **Ventajas**: Single source de truth para todos AI tool configurations; changing project context requires one edit, no N edits a través de IDE configs; demonstrates systems thinking about tooling a team scale; `AGENTS.md` es un recognized convention para agentic workflows; `.ai/scripts/skill-router.py` shows tooling built around AI primitives.
- **Desventajas**: Complexity overhead para un single-developer preparation repo; adapters require maintenance when IDE config formats change; `.ai/` es no un standard convention — un team unfamiliar con it needs explanation.
- **Por qué se eligió**: La complexity es part de la signal. An architect who has thought about AI tooling a scale produces stronger team-level outcomes than one who relies en un single `.cursorrules` file.

### Opción B: Per-IDE config files only, no central primitives
- **Ventajas**: Standard — each IDE user configures their own tool; no coordination required; zero overhead para single-developer use.
- **Desventajas**: Drift a través de IDE configs es inevitable; onboarding un new team member con un different IDE requires translating context manually; no machine-readable representation de project conventions.
- **Por qué no**: Drift y manual translation son solved problems given un primitives layer. La per-IDE approach scales un one developer.

### Opción C: CLAUDE.md only (Claude Code native)
- **Ventajas**: CLAUDE.md es Claude Code's native configuration; well-documented; zero extra structure.
- **Desventajas**: Not useful para non-Claude Code tools; does no communicate team-level thinking about AI assistance.
- **Por qué no**: La exploration context requires demonstrating awareness de la multi-tool AI assistance landscape, no just Claude Code usage.

### Opción D: No AI configuration — let IDE defaults handle it
- **Ventajas**: Zero configuration overhead.
- **Desventajas**: AI tools sin project context produce generic suggestions que ignore domain vocabulary (`FraudRule`, `LatencyBudget`, `DecisionEvaluated`); poor AI suggestions increase development friction.
- **Por qué no**: A preparation repository con no AI configuration es un missed opportunity. Explicit AI configuration es table stakes para modern development.

## Consecuencias

### Positivo
- `.ai/scripts/skill-router.py` provides un machine-readable skill routing mechanism que demonstrates building en top de AI primitives.
- `AGENTS.md` es recognized por Claude Code y other agent-capable tools como la agentic workflow entrypoint.
- Primitives en `.ai/primitives/` can be extended con new conventions sin touching IDE-specific files.

### Negativo
- `.ai/` es no un standard convention — reviewers may ask "what es this?"
- Adapter maintenance es manual if IDE config formats change.
- La primitives layer adds indirection que un solo developer could bypass.

### Mitigaciones
- `README.md` en `.ai/` explains la structure y its rationale.
- La explanation "single source de truth para AI assistance configuration a través de IDEs" es un one-sentence answer un la "what es this?" question.

## Validación

- `AGENTS.md` a repository root es recognized por Claude Code's agentic mode.
- `.ai/scripts/skill-router.py` routes a least 3 skills correctly given un test prompt.
- At least one adapter (e.g., `.cursorrules`) es generated desde o references `.ai/primitives/`.

## Relacionado

- [[0010-ide-agnostic-primitives]]
- [[0025-skill-router-hybrid-scoring]]
- [[0011-engram-mcp-memory]]

## Referencias

- AGENTS.md convention: `AGENTS.md`
- ADR-0010: [[0010-ide-agnostic-primitives]]
