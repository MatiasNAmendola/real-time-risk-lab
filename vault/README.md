---
title: Risk Decision Platform — Obsidian Vault
tags: [meta, obsidian, pkm]
created: 2026-05-07
---

# Risk Decision Platform Vault

This vault contains all material related to the risk decision platform exploration: architecture decisions, PoC documentation, methodology, concept notes, and build logs. The use case is a production-grade fraud detection system; the implementation is a learning and exploration artifact.

## What is Obsidian

Obsidian is a local-first Markdown knowledge base that renders bidirectional links between notes. Notes link to each other via wikilinks de estilo Obsidian, and the graph view shows how concepts cluster.

## How to Open

1. Install Obsidian from https://obsidian.md (free).
2. Launch Obsidian → click "Open folder as vault".
3. Select this `vault/` directory.
4. The graph view (`Ctrl/Cmd + G`) gives a visual map of all connections.

## Navigation

Start at [[Risk-Platform-Overview]] (the root MOC) and follow wikilinks outward.

Suggested paths:
- Architecture deep-dive: [[Risk-Platform-Overview]] → [[Architecture]] → [[Clean-Architecture]] / [[Hexagonal-Architecture]]
- Methodology: [[Risk-Platform-Overview]] → [[Architecture-Question-Bank]] → [[Architectural-Anchors]]
- PoC overview: [[Risk-Platform-Overview]] → any note in `03-PoCs/`
- Build log: [[2026-05-07-build-log]]

## Tagging Conventions

Tags are hierarchical using `/`:
- `#pattern/structural` — design patterns (Clean, Hexagonal, etc.)
- `#pattern/resilience` — Circuit Breaker, Bulkhead, Retry
- `#pattern/async` — Outbox, DLQ, Event Versioning
- `#methodology/principle` — design principles and architectural anchors
- `#methodology/anti-pattern` — patterns to avoid
- `#decision/accepted` — ADRs with accepted status
- `#poc` — proof-of-concept notes
- `#concept` — conceptual notes

## Recommended Community Plugins (not installed)

Install these after opening the vault for a better experience:
- **Dataview** — query notes like a database (e.g., list all ADRs by status)
- **Templater** — advanced template engine for new notes
- **Excalidraw** — embed diagrams directly in notes

## File Structure

```
00-MOCs/       Maps of Content — entry points into the knowledge base
01-Build-Log/  Build logs (blow-by-blow of each session)
02-Decisions/  Architecture Decision Records (ADRs)
03-PoCs/       Proof-of-concept documentation
04-Concepts/   Conceptual notes (patterns, techniques, tools)
05-Methodology/ Design methodology, question bank, anti-patterns
06-Daily/      Daily notes
08-References/ External references and docs
```
