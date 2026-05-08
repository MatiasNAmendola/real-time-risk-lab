# Engram — Memoria persistente del proyecto

Engram MCP provee memoria persistente entre sesiones para todos los agentes que trabajen en este repo.

## Identificadores del proyecto

- **Project key**: `real-time-risk-lab`
- **Project name**: Real-Time Risk Lab — Technical Exploration

## Topic keys establecidos

| Topic key | Contenido |
|---|---|
| `risk-platform/exploration-state` | Estado general de preparacion: que esta listo, que falta, progreso diario |
| `riskplatform/poc/no-vertx-clean-engine` | Decisiones, bugs, descubrimientos del PoC bare-javac |
| `riskplatform/poc/vertx-layer-as-pod-eventbus` | Decisiones del PoC Vert.x multi-modulo |
| `riskplatform/poc/vertx-layer-as-pod-http` | Decisiones de la plataforma Vert.x completa |
| `riskplatform/poc/k8s-local` | Configuracion y troubleshooting de k8s local |
| `riskplatform/primitives/system` | El sistema .ai/ de primitivas (este sistema) |
| `riskplatform/adr/<N>` | ADRs individuales (ej. `riskplatform/adr/001`) |

## Cuándo guardar (mem_save)

Guardar INMEDIATAMENTE (sin esperar) despues de:

- Tomar una decision de arquitectura o diseno.
- Encontrar y resolver un bug (incluir causa raiz).
- Establecer una convencion o patron.
- Completar una feature o PoC.
- Descubrir algo no obvio sobre el stack o la configuracion.
- Cambiar la configuracion de un servicio.

## Cuándo buscar (mem_search)

Buscar proactivamente:

- Al inicio de CADA sesion de trabajo.
- Antes de comenzar una nueva feature (puede haber trabajo previo).
- Cuando se hace referencia a algo ambiguo ("la regla de..." "el patron de...").
- Cuando se recibe un error inesperado (puede haber sido resuelto antes).

## Comandos clave

```
# Inicio de sesion:
mem_current_project()
mem_context(project: "real-time-risk-lab")

# Busqueda:
mem_search(query: "risk-platform <topic>")
mem_get_observation(id: "<id>")   # para contenido completo (search trunca)

# Guardar decision:
mem_save(
  title: "Decision: <verbo + que>",
  type: "decision",
  scope: "project",
  project: "real-time-risk-lab",
  topic_key: "riskplatform/<subtema>/<clave>",
  content: "What: ...\nWhy: ...\nWhere: ...\nLearned: ..."
)

# Guardar bug fix:
mem_save(
  title: "Fix: <descripcion>",
  type: "bugfix",
  topic_key: "riskplatform/poc/<poc-name>",
  project: "real-time-risk-lab",
  content: "Root cause: ...\nFix: ...\nFiles: ...\nLearned: ..."
)

# Fin de sesion (OBLIGATORIO):
mem_session_summary(
  project: "real-time-risk-lab",
  goal: "...",
  instructions: "...",
  discoveries: "...",
  accomplished: "...",
  next_steps: "...",
  relevant_files: ["..."]
)
```

## Formato de contenido recomendado

```
What: <que se hizo o descubrio>
Why: <por que fue necesario>
Where: <archivo(s) afectado(s)>
Learned: <aprendizaje clave para el proximo agente>
```

## Notas importantes

- `mem_search` devuelve resultados truncados. Siempre usar `mem_get_observation(id)` para el contenido completo cuando el truncado no es suficiente.
- El `topic_key` es el mecanismo de "upsert": si ya existe una observacion con ese key, se actualiza en lugar de crear una nueva.
- Usar `mem_suggest_topic_key` si no se sabe que key usar.
- Engram persiste entre sesiones y entre diferentes agentes. Lo que un agente guarda, otro lo puede leer.
