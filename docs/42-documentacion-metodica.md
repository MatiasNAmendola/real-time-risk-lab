# 42 — Sistema de documentación metódica

Este repo no usa una sola carpeta de documentación. Usa varias superficies con responsabilidades distintas para que humanos y agentes IA puedan operar el proyecto sin contexto oral.

## Capas

| Capa | Propósito | Ejemplo |
|---|---|---|
| `docs/` | Guías operativas, explicaciones largas y material share-ready. | `docs/41-cqrs-event-sourcing-transacciones.md` |
| `vault/` | Knowledge base Obsidian: MOCs, ADRs, conceptos, PoCs y metodología. | `vault/03-PoCs/nestjs-distributed-transactions.md` |
| `.ai/context/` | Contexto compacto para agentes. | `.ai/context/poc-inventory.md` |
| `.ai/primitives/` | Reglas, skills, workflows y hooks. | `.ai/primitives/rules/documentation-system.md` |
| Adapters IDE/CLI | Traducción de las reglas a cada herramienta. | `.cursor/rules/*.mdc`, `.kiro/steering/*.md` |

## Cómo documentar un cambio relevante

1. **Definir la fuente primaria.**
   - Decisión: ADR en `vault/02-Decisions/`.
   - Concepto: nota en `vault/04-Concepts/`.
   - PoC: nota en `vault/03-PoCs/` + README propio.
   - Guía operativa: `docs/`.
2. **Actualizar los índices.**
   - MOC correspondiente en `vault/00-MOCs/`.
   - `docs/00-START-HERE.md` si cambia el recorrido recomendado.
   - `.ai/context/*` si cambia inventario, stack o estado.
3. **Actualizar primitivas si el cambio crea una forma de trabajar repetible.**
4. **Propagar a adapters si otros IDE/CLI deben seguir la misma regla.**
5. **Auditar consistencia.**

```bash
python3 .ai/scripts/consistency-auditor.py all --report-md
python3 .ai/scripts/verify-primitives.sh
```

## Regla práctica

No documentar sólo en `docs/` cuando el tema sea una PoC, patrón o decisión. `docs/` explica; `vault/` indexa y preserva conocimiento; `.ai/` vuelve ejecutable la metodología para agentes.


## Política de idioma

- `docs/` y `vault/` se mantienen en español porque son documentación humana.
- `.ai/primitives/` y adapters IDE/CLI se mantienen en inglés conciso porque son instrucciones para agentes y consumen contexto en cada herramienta.
- Los términos técnicos estándar se preservan en inglés: CQRS, Event Sourcing, Saga pattern, outbox pattern, circuit breaker, idempotency, snapshot, read model, ledger, smoke test.
