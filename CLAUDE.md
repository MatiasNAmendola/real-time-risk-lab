@AGENTS.md

---

# CLAUDE.md — Configuracion especifica de Claude Code

Este archivo extiende `AGENTS.md` con configuracion especifica para el harness de Claude Code.

## Hooks recomendados (.claude/settings.json)

Agregar los siguientes hooks para automatizar comportamientos:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash .ai/scripts/check-secrets.sh 2>/dev/null || true"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash .ai/scripts/run-module-tests.sh 2>/dev/null || true"
          }
        ]
      }
    ]
  }
}
```

Ver hooks completos en: `.ai/primitives/hooks/`

## Sub-agents disponibles

Los sub-agents en `.claude/agents/` corresponden a los skills atomicos:

```bash
# Instalar todos los sub-agents:
./.ai/adapters/claude-code/install.sh

# Usar un skill especifico:
# "usa el skill add-rest-endpoint"
# "ejecuta el workflow new-feature-atdd"
```

## Slash commands sugeridos

Una vez instalados los sub-agents, se pueden invocar como:
- "usa el sub-agent add-fraud-rule"
- "ejecuta add-otel-custom-span para el use case de evaluacion"
- "bootstrap-new-poc para una nueva PoC de CQRS"

## Engram MCP

Este proyecto tiene Engram MCP configurado. Al iniciar sesion:

1. `mem_current_project()` — detectar proyecto
2. `mem_context(project: "risk-decision-platform")` — cargar contexto
3. `mem_search(query: "risk-platform current state")` — estado actual

Al finalizar: `mem_session_summary(...)` es OBLIGATORIO.

Ver: `.ai/context/engram.md` y `.ai/primitives/hooks/session-start-engram-load.md`

## Verificar el sistema de primitivas

```bash
./.ai/scripts/verify-primitives.sh
```

## Primitive-first protocol (MANDATORY)

Antes de lanzar un sub-agente o hacer Edit/Write significativo:

1. Ejecuta: `python3 .ai/scripts/skill-router.py --top 3 "<descripcion de la tarea>"`
2. Lee el skill top-1. Si su confianza > 0.5 y el intent matches, citalo en el prompt: `SKILL: Load .ai/primitives/skills/<name>.md as your guide.`
3. Si la tarea es multi-step, invoca `python3 .ai/scripts/workflow-runner.py --dry-run <workflow>` primero.
4. Loggea la decision en `.ai/logs/skill-routing-YYYY-MM-DD.jsonl`.
5. Si NO hay skill aplicable, agrega uno usando workflow `add-architecture-decision.md` antes de improvisar.

El hook PreToolUse en `.claude/settings.json` automatiza los pasos 1+4. La invocacion manual de workflow-runner es responsabilidad del orchestrator.

```bash
# Ejemplos rapidos:
python3 .ai/scripts/skill-router.py --top 3 "agregar un consumer Kafka"
python3 .ai/scripts/workflow-runner.py --dry-run add-comm-pattern
python3 .ai/scripts/usage-stats.py
```

## Reglas de trabajo para Claude Code

1. Antes de implementar: leer la rule y el skill correspondiente.
2. Antes de commitear: verificar que los tests pasan.
3. No tocar `poc/`, `tests/`, `cli/`, `docs/`, `vault/` sin necesidad explicita.
4. Guardar en Engram decisiones, bugs, y descubrimientos INMEDIATAMENTE (no al final).
5. `mem_session_summary` al terminar, sin falta.
