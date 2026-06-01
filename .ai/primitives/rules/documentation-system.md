---
name: documentation-system
intent: Keep docs, vault, AI context, primitives and IDE/CLI adapters consistent with low token cost
scope: [documentation, primitives, adapters]
---

# Rule: documentation system

## Language policy

- Human-facing documentation in `docs/` and `vault/` stays in Spanish.
- Agent-facing instructions in `.ai/primitives/`, `.cursor/`, `.windsurf/`, `.github/instructions/`, `.kiro/steering/`, `.continue/` and `.claude/agents/` stay in concise English.
- Keep standard terms in English everywhere: CQRS, Event Sourcing, Saga pattern, outbox pattern, circuit breaker, idempotency, snapshot, read model, ledger, smoke test.

## Layers

| Layer | Purpose | Update when |
|---|---|---|
| `docs/` | Human guides and long-form explanations. | Run/verify/explanation path changes. |
| `vault/` | Obsidian KB: MOCs, ADRs, concepts, PoCs, methodology. | A reusable pattern, PoC, decision or concept is added. |
| `.ai/context/` | Compact context for agents. | Inventory, stack, architecture or state changes. |
| `.ai/primitives/` | Agent rules, skills, workflows and hooks. | A repeatable process or guardrail appears. |
| IDE/CLI adapters | Minimal mapping from primitives to each tool. | A rule must apply outside Codex too. |

## Rule

Do not document relevant changes only in `docs/`. For each non-trivial change decide whether it needs:

1. `docs/` operational guide;
2. `vault/` concept, PoC note or ADR;
3. `.ai/context/` inventory/state update;
4. `.ai/primitives/` rule/skill/workflow;
5. adapter updates for Claude Code, Cursor, Windsurf, Copilot, Kiro, Continue, Codex/opencode.

## Matrix

| Change | docs/ | vault/ | .ai/context | .ai/primitives | adapters |
|---|---:|---:|---:|---:|---:|
| New PoC | yes | yes (`03-PoCs`) | yes | if repeatable | if agents need it |
| New pattern | if guide needed | yes (`04-Concepts`) | if architecture changes | if it creates a rule | yes |
| New decision | optional | yes (`02-Decisions`) | yes (`decisions-log`) | usually no | no |
| New command/test runner | yes | optional | if inventoried | if guardrail | if agents run it |
| Minor implementation change | usually no | usually no | no | no | no |

## Closeout checklist

- [ ] `docs/00-START-HERE.md` points to the right source.
- [ ] `vault/00-MOCs/*` links new concepts/PoCs/ADRs when relevant.
- [ ] `vault/02-Decisions/_index.md` includes new ADRs.
- [ ] `vault/03-PoCs/*` exists for new PoCs.
- [ ] `vault/04-Concepts/*` exists for reusable concepts.
- [ ] `.ai/context/{poc-inventory,architecture,stack,exploration-state}.md` is current when relevant.
- [ ] Skills/workflows do not cite migrated or missing docs.
- [ ] IDE/CLI adapters do not contradict repo rules.
- [ ] Run `python3 .ai/scripts/consistency-auditor.py all --report-md`.
- [ ] Run `./.ai/scripts/verify-primitives.sh`.
