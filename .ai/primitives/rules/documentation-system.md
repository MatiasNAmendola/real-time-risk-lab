---
name: documentation-system
intent: Mantener documentación consistente entre docs/, vault/, .ai/context y adapters IDE/CLI
scope: [documentation, primitives, adapters]
---

# Regla: documentación metódica y multi-superficie

## Capas canónicas

| Capa | Propósito | Cuándo actualizar |
|---|---|---|
| `docs/` | Guías operativas y explicaciones largas para humanos/reviewers. | Cuando cambia cómo entender, correr o verificar el sistema. |
| `vault/` | Knowledge base Obsidian: MOCs, ADRs, conceptos, PoCs, metodología. | Cuando se agrega un patrón, PoC, decisión o concepto reutilizable. |
| `.ai/context/` | Contexto compacto para agentes IA. | Cuando cambia el inventario, stack, arquitectura o estado de exploración. |
| `.ai/primitives/` | Reglas, skills y workflows ejecutables por agentes. | Cuando aparece una forma nueva de trabajar o un guardrail repetible. |
| Adapters IDE/CLI | Traducción mínima de las primitivas al formato de cada herramienta. | Cuando una regla nueva debe aplicar fuera de Codex. |

## Regla de oro

No alcanza con documentar sólo en `docs/`. Para cada cambio relevante, decidir explícitamente si necesita:

1. guía operativa en `docs/`;
2. nota conceptual o PoC en `vault/`;
3. inventario/estado en `.ai/context/`;
4. rule/skill/workflow en `.ai/primitives/`;
5. actualización de adapters para Claude Code, Cursor, Windsurf, Copilot, Kiro, Continue, Codex/opencode.

## Matriz de decisión

| Cambio | docs/ | vault/ | .ai/context | .ai/primitives | adapters |
|---|---:|---:|---:|---:|---:|
| Nueva PoC | Sí | Sí (`03-PoCs`) | Sí | Si hay patrón repetible | Sí si aplica a agentes |
| Nuevo patrón arquitectónico | Sí si necesita guía | Sí (`04-Concepts`) | Sí si afecta arquitectura | Sí si genera regla | Sí |
| Nueva decisión | Opcional | Sí (`02-Decisions`) | Sí (`decisions-log`) | No, salvo workflow | No |
| Nuevo comando/test runner | Sí | Opcional | Sí si entra al inventario | Sí si es guardrail | Sí si agentes deben usarlo |
| Cambio menor de implementación | No necesariamente | No necesariamente | No | No | No |

## Checklist antes de cerrar

- [ ] `docs/00-START-HERE.md` apunta a la fuente correcta.
- [ ] `vault/00-MOCs/*` linkea el concepto/PoC nuevo si es relevante.
- [ ] `vault/02-Decisions/_index.md` incluye ADRs nuevos.
- [ ] `vault/03-PoCs/*` existe para PoCs nuevas.
- [ ] `vault/04-Concepts/*` existe para conceptos reutilizables.
- [ ] `.ai/context/poc-inventory.md`, `architecture.md`, `stack.md` o `exploration-state.md` están actualizados si corresponde.
- [ ] Skills/workflows no referencian docs migrados o inexistentes.
- [ ] Adapters IDE/CLI no contradicen las reglas del repo.
- [ ] Ejecutar `python3 .ai/scripts/consistency-auditor.py all --report-md` y revisar score/gaps.

## Política de idioma

La documentación del repo se mantiene en español, preservando términos técnicos universales en inglés cuando sean el estándar de la industria: CQRS, Event Sourcing, Saga pattern, outbox pattern, circuit breaker, idempotency, snapshot, read model, ledger, smoke test.
