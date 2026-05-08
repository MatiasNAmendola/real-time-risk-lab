---
name: session-end-engram-save
trigger: session-end
harnesses: [claude-code]
---

# Hook: session-end-engram-save

## Proposito

Al cerrar una sesion, persistir el resumen en Engram para que la proxima sesion (o agente) tenga continuidad.

## Pasos (ejecutar antes de terminar cada sesion)

1. Guardar resumen con `mem_session_summary`:
   ```
   mem_session_summary(
     project: "riskplatform/real-time-risk-lab",
     goal: "<objetivo de esta sesion>",
     instructions: "<preferencias del usuario aprendidas>",
     discoveries: "<hallazgos tecnicos importantes>",
     accomplished: "<lista de lo completado>",
     next_steps: "<lista de lo pendiente>",
     relevant_files: ["<archivo1>", "<archivo2>"]
   )
   ```

2. Si hubo decisiones importantes no guardadas aun, usar `mem_save` para cada una.

3. Actualizar `.ai/context/exploration-state.md` con el estado actual.

## Implementacion en Claude Code (.claude/settings.json)

```json
{
  "hooks": {
    "Stop": [
      {
        "type": "command",
        "command": "bash -c 'echo \"Session ended at $(date). Remember to run mem_session_summary.\" >> /tmp/session-reminders.log'"
      }
    ]
  }
}
```

## Que guardar en mem_session_summary

### Siempre:
- Features completadas (con nombre del archivo).
- Decisiones tomadas (aunque sean pequeñas).
- Bugs encontrados y cómo se resolvieron.
- Lo que queda pendiente (next_steps).

### Si aplica:
- Problemas de setup o configuracion y como se resolvieron.
- Versiones o dependencias que causaron problemas.
- Patrones o convenciones nuevas establecidas.

## Notas

- `mem_session_summary` es OBLIGATORIO. No es opcional.
- Si la sesion fue corta (< 15 min) o solo fue lectura: igual guardar, aunque sea breve.
- El resumen debe ser util para el proximo agente, no solo para el humano.
