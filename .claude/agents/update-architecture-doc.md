---
name: update-architecture-doc
intent: Update architecture documentation across docs, vault, AI context and adapters when needed
inputs: [doc_path, section, new_content]
preconditions:
  - docs/ exists for human guides
  - vault/ exists for ADRs, concepts, PoCs and methodology
  - .ai/context/exploration-state.md exists
postconditions:
  - Primary source is updated
  - Indexes and MOCs are synchronized
  - exploration-state.md reflects progress when relevant
  - Engram is updated when available
related_rules: [naming-conventions, documentation-system]
---

# Skill: update-architecture-doc

## Use when

- A PoC or feature is completed.
- A reusable topic must be documented.
- Architecture Q&A needs updates in `vault/05-Methodology/Architecture-Question-Bank.md`.
- Remaining work must be recorded.

## Steps

1. Classify via `.ai/primitives/rules/documentation-system.md`:
   - decision → `vault/02-Decisions/<NNNN>-*.md` + `_index.md`;
   - concept → `vault/04-Concepts/*.md` + relevant MOC;
   - PoC → `vault/03-PoCs/*.md` + PoC README + `.ai/context/poc-inventory.md`;
   - operational guide → `docs/*.md`;
   - repeatable agent behavior → `.ai/primitives/{rules,skills,workflows}` + adapters.

2. Update the primary source first, then indexes/context:
   - `docs/00-START-HERE.md` if the recommended path changes;
   - `vault/00-MOCs/*` for new concepts/PoCs/ADRs;
   - `.ai/context/{architecture,poc-inventory,stack,exploration-state}.md` when inventory/stack/state changes.

3. If agents must follow it, update a minimal adapter instruction:
   - `AGENTS.md` / Codex/opencode;
   - `.claude/agents` or `CLAUDE.md`;
   - `.cursor/rules/*.mdc`;
   - `.windsurf/rules/*.md`;
   - `.github/copilot-instructions.md` or `.github/instructions/*.instructions.md`;
   - `.kiro/steering/*.md`;
   - `.continue/prompts/*.prompt`.

4. Run:
   ```bash
   python3 .ai/scripts/consistency-auditor.py all --report-md
   ./.ai/scripts/verify-primitives.sh
   ```

5. Save to Engram when available.

6. Commit as `docs: update <doc-name> with <brief description>`.

## Notes

- Do not edit `docs/` or `vault/` without a real need.
- Do not document a PoC, ADR or reusable concept only in `docs/`; mirror it in `vault/`.
- Keep this primitive concise and in English; human docs can stay Spanish.
