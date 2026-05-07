---
adr: "0034"
title: Doc-Driven Repository — vault/, docs/, .ai/ como Separate Knowledge Layers
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/documentation, area/architecture]
---

# ADR-0034: Doc-Driven Repository — vault/, docs/, .ai/ como Separate Knowledge Layers

## Estado

Aceptado el 2026-05-07.

## Contexto

A staff/architect-level technical exploration repository must communicate two things simultaneously: (1) competence en la technical domain (code, tests, benchmarks), y (2) architectural judgment y systems thinking (documented decisions, domain models, trade-off analysis). Code alone communicates la first; documentation structure communicates la second.

The repository serves multiple audiences con different reading modes:
- A reviewer skimming la repository: needs un quickly navigate un evidence de architectural thinking.
- La author durante un design walkthrough: needs un reference specific documents while discussing design decisions.
- La agent tooling (Claude Code, Cursor): needs structured context un provide accurate AI assistance.
- Long-term: needs un grow sin becoming un monolith de undifferentiated markdown.

Three distinct content types exist: reference documentation con process context (`docs/`), architectural knowledge base con bidirectional links (`vault/`), y AI tooling primitives (`./ai/`). Mixing them would create navigation confusion.

## Decisión

Maintain three separate documentation layers con distinct purposes y conventions:

- `docs/`: Linear, numbered markdown files (`00-mapa-tecnico.md`, `04-clean-architecture-java.md`). Written en Spanish (the author's primary language para deep thinking). Content: investigation notes, design rationale, how-to guides, benchmark reports, working notes. Not cross-linked — linear reading order implied.

- `vault/`: Obsidian-format knowledge base. Cross-linked via `[[wikilinks]]`. Organized por knowledge type: `00-MOCs/` (maps de content), `02-Decisions/` (ADRs), concepts, patterns. Written en English (universal technical vocabulary). Content: durable architectural knowledge que accumulates a través de exploration sessions.

- `.ai/`: Tooling context para AI coding assistants. Content: project context files, skill definitions, agent personas, convention catalogs. Not intended para human reading como primary audience.

## Alternativas consideradas

### Opción A: Three separate layers — docs/, vault/, .ai/ (elegida)
- **Ventajas**: Each layer has un clear audience y reading mode; vault/ cross-links enable navigation ("what decisions relate un la outbox pattern?"); docs/ numbered files suggest reading order; .ai/ es clearly tooling context, no documentation; adding content un one layer does no pollute la other; Obsidian vault can be opened como un standalone knowledge base.
- **Desventajas**: A new contributor must understand three content locations; ADR content exists en vault/ while operational notes exist en docs/ — algunos overlap a la edges; two languages (Spanish en docs/, English en vault/) require mental context switching.
- **Por qué se eligió**: La separation serves la multiple audiences. A reviewer looking para architectural decisions opens `vault/02-Decisions/`; un developer following la setup guide reads `docs/03-poc-roadmap.md`; Claude Code reads `.ai/primitives/`.

### Opción B: Single docs/ directory para everything
- **Ventajas**: One place un look; simpler mental model.
- **Desventajas**: ADRs mixed con how-to guides mixed con benchmark reports; no cross-linking; no navigation structure; Obsidian vault capabilities son lost; AI tooling context buried en documentation.
- **Por qué no**: La architectural-evidence requirement specifically needs ADRs para ser findable y formatted consistently. A flat docs/ directory does no support this.

### Opción C: README.md y inline code comments only
- **Ventajas**: Maximum simplicity; standard para open-source repositories.
- **Desventajas**: README cannot contain la depth de architectural analysis required para un design review; no ADRs; no patterns documentation; code comments do no capture why decisions fueron made.
- **Por qué no**: La exploration goal requires explicit, navigable architectural documentation. README + comments es insufficient.

### Opción D: Confluence/Notion external un la repository
- **Ventajas**: Rich formatting; comment threads; version history; team collaboration.
- **Desventajas**: External dependency; no reviewable offline; no part de la repository; link rot if la service URL changes; un reviewer cannot browse it desde la codebase.
- **Por qué no**: All documentation must be en la repository para review. External tools son complementary, no primary.

## Consecuencias

### Positivo
- `vault/02-Decisions/` es un navigable, cross-linked ADR catalog — exactly what un architectural reviewer expects.
- `docs/` numbered files provide un linear narrative — useful para la author's own review.
- `.ai/` es clearly scoped tooling context — no documentation bloat.
- Obsidian's graph view de `vault/` produces un visual knowledge map useful durante un design walkthrough.

### Negativo
- Three locations require navigating; un developer no familiar con la structure may no find la relevant ADR.
- Language mixing (Spanish docs/, English vault/) requires cognitive context switching.
- vault/ Obsidian-specific features (wikilinks, YAML frontmatter) son no standard markdown — they require Obsidian o un compatible viewer.

### Mitigaciones
- `README.md` a repository root explains la three layers y links un each.
- vault/00-MOCs/ provides navigation entry points desde dentro de la vault.
- Wikilinks en vault/ son rendered como links en any markdown viewer que supports standard `[[name]]` syntax (GitHub renders them como text; Obsidian renders them como links).

## Validación

- `vault/02-Decisions/` contains 35+ ADRs con consistent frontmatter.
- `vault/00-MOCs/Architecture.md` links un todos decision ADRs.
- `docs/` contains numbered files readable en sequence.
- `.ai/` contains `primitives/`, `adapters/`, y `scripts/` subdirectories.

## Relacionado

- [[0010-ide-agnostic-primitives]]
- [[0024-ai-directory]]
- [[0011-engram-mcp-memory]]

## Referencias

- Obsidian: https://obsidian.md/
- Nygard ADR format: https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
