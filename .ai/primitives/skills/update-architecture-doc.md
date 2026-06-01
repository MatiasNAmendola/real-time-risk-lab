---
name: update-architecture-doc
intent: Actualizar documentación arquitectónica en docs/, vault/, .ai/context y adapters cuando corresponda
inputs: [doc_path, section, new_content]
preconditions:
  - docs/ existe para guías operativas
  - vault/ existe para ADRs, conceptos, PoCs y metodología
  - .ai/context/exploration-state.md existe
postconditions:
  - La fuente primaria correcta queda actualizada
  - Los índices/MOCs quedan sincronizados
  - exploration-state.md refleja progreso si corresponde
  - Engram actualizado cuando la herramienta esté disponible
related_rules: [naming-conventions, documentation-system]
---

# Skill: update-architecture-doc

## When to Use

- After completing a PoC or feature.
- When a new topic needs to be documented.
- To add questions and analyses to the architecture question bank (`vault/05-Methodology/Architecture-Question-Bank.md`).
- To record what remains to be done.

## Steps

1. Clasificar el cambio según `.ai/primitives/rules/documentation-system.md`:
   - decisión → `vault/02-Decisions/<NNNN>-*.md` + `_index.md`;
   - concepto → `vault/04-Concepts/*.md` + MOC relevante;
   - PoC → `vault/03-PoCs/*.md` + README de la PoC + `.ai/context/poc-inventory.md`;
   - guía operativa o explicación larga → `docs/*.md`;
   - regla repetible para agentes → `.ai/primitives/rules|skills|workflows` + adapters IDE/CLI.

2. Editar la fuente primaria y luego los índices:
   - `docs/00-START-HERE.md` si cambia el recorrido recomendado;
   - `vault/00-MOCs/*` si hay un concepto/PoC/ADR nuevo;
   - `.ai/context/architecture.md`, `poc-inventory.md`, `stack.md` o `exploration-state.md` si cambia inventario/stack/estado.

3. Si el cambio afecta cómo trabajan agentes, propagar una instrucción mínima a:
   - `AGENTS.md` / `.ai/adapters/codex` para Codex/opencode;
   - `.claude/agents` o `CLAUDE.md` para Claude Code;
   - `.cursor/rules/*.mdc`;
   - `.windsurf/rules/*.md`;
   - `.github/copilot-instructions.md` o `.github/instructions/*.instructions.md`;
   - `.kiro/steering/*.md`;
   - `.continue/prompts/*.prompt`.

4. Ejecutar auditoría:
   ```bash
   python3 .ai/scripts/consistency-auditor.py all --report-md
   ./.ai/scripts/verify-primitives.sh
   ```

5. Save to Engram cuando esté disponible:
   ```
   mem_save(
     title: "Risk platform state update: <date>",
     type: "discovery",
     topic_key: "riskplatform/risk-platform/state",
     project: "riskplatform/real-time-risk-lab",
     content: <summary of changes and current state>
   )
   ```

6. Commit: `docs: update <doc-name> with <brief description>`.

## Notes
- Do NOT touch `docs/` or `vault/` without a real need. They are technical references, not edited like code.
- Do NOT document only in `docs/` when the topic is a PoC, ADR or reusable concept; mirror it in `vault/`.
- `exploration-state.md` is the daily progress tracker. Update at minimum at the start and end of each work session.
