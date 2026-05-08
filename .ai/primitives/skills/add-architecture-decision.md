---
name: add-architecture-decision
intent: Registrar una decision de arquitectura en formato ADR en decisions-log.md y en Engram
inputs: [adr_title, context, decision, consequences, status]
preconditions:
  - .ai/context/decisions-log.md existe
postconditions:
  - ADR agregado al decisions-log.md con numero secuencial
  - Decision guardada en Engram con topic_key riskplatform/adr/<numero>
  - Commit: "docs(adr): ADR-<N> <titulo>"
related_rules: []
---

# Skill: add-architecture-decision

## Formato de ADR (en decisions-log.md)

```markdown
## ADR-<N>: <Titulo>

**Status**: proposed | accepted | deprecated | superseded by ADR-<M>
**Date**: YYYY-MM-DD
**Deciders**: <quien decidio>

### Contexto
<Por que se necesitaba tomar esta decision. Problema a resolver.>

### Decision
<La decision tomada en una frase clara.>

### Consecuencias positivas
- <consecuencia 1>

### Consecuencias negativas / riesgos
- <riesgo 1>

### Alternativas consideradas
- <alternativa A>: <por que no se eligio>
```

## Pasos

1. Leer `.ai/context/decisions-log.md` para obtener el numero siguiente.
2. Agregar el ADR al final del archivo.
3. Guardar en Engram:
   ```
   mem_save(
     title: "ADR-<N>: <titulo>",
     type: "decision",
     topic_key: "riskplatform/adr/<N>",
     project: "riskplatform/risk-platform-practice",
     content: <contenido completo del ADR>
   )
   ```
4. Commit con mensaje `docs(adr): ADR-<N> <titulo en kebab-case>`.

## Notas
- Un ADR registra la decision, no el diseno detallado. Para diseno: ver `sdd-design` skill.
- Los ADRs son inmutables una vez `accepted`. Para revertir: crear nuevo ADR que supersede.
- Mantener maximos 2-3 parrafos por seccion.
