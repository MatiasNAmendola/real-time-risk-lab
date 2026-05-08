---
name: session-start-engram-load
trigger: session-start
harnesses: [claude-code]
---

# Hook: session-start-engram-load

## Proposito

Al iniciar una sesion de trabajo, cargar el contexto del proyecto desde Engram para que el agente tenga memoria de sesiones anteriores sin pedirlo explicitamente.

## Pasos (ejecutar al inicio de cada sesion)

1. Detectar proyecto actual:
   ```
   mem_current_project()
   → project: riskplatform/real-time-risk-lab
   ```

2. Cargar contexto reciente:
   ```
   mem_context(project: "riskplatform/real-time-risk-lab")
   ```

3. Si el contexto no es suficiente, buscar especificamente:
   ```
   mem_search(query: "risk-platform current state")
   mem_get_observation(id: <id-del-resultado>)
   ```

4. Buscar trabajo en progreso:
   ```
   mem_search(query: "risk-platform in progress")
   ```

5. Reportar al usuario el estado cargado en 2-3 lineas.

## Implementacion en Claude Code (.claude/settings.json)

```json
{
  "hooks": {
    "SessionStart": [
      {
        "type": "command",
        "command": "bash .ai/scripts/engram-bootstrap.sh"
      }
    ]
  }
}
```

Ver `.ai/scripts/engram-bootstrap.sh` para el script completo.

## Comportamiento esperado

Al inicio de sesion el agente debe poder responder:
- Cual fue el ultimo trabajo realizado.
- Que PoCs estan completas vs en progreso.
- Que decisiones arquitecturales importantes se tomaron.
- Que queda pendiente para validacion.

Sin necesitar que el usuario repita el contexto.

## Notas

- Si Engram no tiene datos: arrancar desde `.ai/context/exploration-state.md`.
- No bloquear el inicio de sesion si Engram falla. Continuar sin memoria persistente.
- Despues de cargar: confirmar al usuario "contexto cargado: <resumen de 1 linea>".
