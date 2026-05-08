---
name: update-architecture-doc
intent: Update an architecture document in docs/ or project state in .ai/context/exploration-state.md
inputs: [doc_path, section, new_content]
preconditions:
  - docs/ exists with files 00-xx
  - .ai/context/exploration-state.md exists
postconditions:
  - Document updated with accurate information
  - exploration-state.md reflects current progress
  - Engram updated with riskplatform/risk-platform/state
related_rules: [naming-conventions]
---

# Skill: update-architecture-doc

## When to Use

- After completing a PoC or feature.
- When a new topic needs to be documented.
- To add questions and analyses to the architecture question bank (docs/09-architecture-question-bank.md).
- To record what remains to be done.

## Steps

1. Identify the document to update:
   - `docs/00-mapa-tecnico.md` — general system map
   - `docs/01-design-conversation-framework.md` — how to decompose systems design problems
   - `docs/02-platform-discovery-questions.md` — discovery questions for platform evaluation
   - `docs/09-architecture-question-bank.md` — architecture Q&A with model analyses
   - `.ai/context/exploration-state.md` — project state (what is ready, what is missing)

2. Edit the corresponding section.

3. Update `.ai/context/exploration-state.md` if the state of something changes.

4. Save to Engram:
   ```
   mem_save(
     title: "Risk platform state update: <date>",
     type: "discovery",
     topic_key: "riskplatform/risk-platform/state",
     project: "riskplatform/risk-platform-practice",
     content: <summary of changes and current state>
   )
   ```

5. Commit: `docs: update <doc-name> with <brief description>`.

## Notes
- Do NOT touch `docs/` without a real need. The docs are technical references, not edited like code.
- `exploration-state.md` is the daily progress tracker. Update at minimum at the start and end of each work session.
