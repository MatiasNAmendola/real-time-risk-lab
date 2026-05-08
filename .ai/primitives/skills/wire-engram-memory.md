---
name: wire-engram-memory
intent: Cargar y guardar contexto del proyecto en Engram MCP al inicio y fin de sesion
inputs: [project_key, session_goal]
preconditions:
  - Engram MCP disponible en el harness (Claude Code con MCP configurado)
  - Project key: riskplatform/risk-platform-practice
postconditions:
  - Contexto previo cargado antes de comenzar trabajo
  - Decisiones y descubrimientos guardados al finalizar
related_rules: []
---

# Skill: wire-engram-memory

## Al inicio de sesion

1. Llamar `mem_current_project` para detectar el proyecto.
2. Llamar `mem_context` para obtener historial reciente.
3. Si hay trabajo previo relevante: `mem_search(query: "risk-platform risk engine")`.
4. Para temas especificos: `mem_search(query: "<tema>")` → `mem_get_observation(id)` para contenido completo.

## Durante la sesion

Guardar con `mem_save` INMEDIATAMENTE despues de:
- Cualquier decision de arquitectura o diseno.
- Descubrimiento de bug o comportamiento inesperado.
- Convencion establecida (naming, patron, estructura).
- Feature completada.

Formato:
```
title: "Fix: <verbo> + <que>"
type: bugfix|decision|architecture|discovery|pattern|config|preference
scope: project
project: riskplatform/risk-platform-practice
topic_key: riskplatform/<subtema>/<especifico>
content: |
  What: <que se hizo>
  Why: <por que>
  Where: <archivos afectados>
  Learned: <aprendizaje clave>
```

## Al fin de sesion

Llamar `mem_session_summary` con:
- Goal: objetivo de la sesion.
- Instructions: preferencias del usuario aprendidas.
- Discoveries: hallazgos tecnicos.
- Accomplished: que se completo.
- Next Steps: que queda pendiente.
- Relevant Files: archivos clave tocados.

## Topic keys de este proyecto

- `risk-platform/exploration-state` — estado general de la prep
- `riskplatform/poc/java-risk-engine` — decisiones del PoC bare-javac
- `riskplatform/poc/java-vertx-distributed` — decisiones del PoC Vert.x distribuido
- `riskplatform/poc/k8s-local` — configuracion k8s
- `riskplatform/poc/vertx-risk-platform` — plataforma Vert.x completa
- `riskplatform/primitives/system` — el sistema .ai/ que estamos construyendo

## Notas
- Ver `.ai/context/engram.md` para la referencia completa.
- En `session-start-engram-load.md` hook: automatizar el paso de inicio.
- En `session-end-engram-save.md` hook: automatizar el paso de cierre.
