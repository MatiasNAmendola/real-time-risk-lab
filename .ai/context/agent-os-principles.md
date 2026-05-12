> Migrated from docs/16-agent-os-principles.md on 2026-05-12 (Fase 3 Round 1).

# 16 — Principios de orquestacion de agentes IA

## Por que este doc

Cuando construis un sistema con muchos agentes IA cooperando, hay decisiones arquitectonicas que se parecen MAS a microservicios que a programacion tradicional. Hay state, hay coordinacion, hay observabilidad, hay token-economy. Aca documentamos los principios que aplicamos en este proyecto.

## Principio 1: Context is the bottleneck

Los LLM tienen window limit y costo por token. Trata el contexto como un recurso escaso, igual que CPU o memoria en sistemas distribuidos.

Mecanismos implementados:

- `context_pruner.py` — dedup + stubs sin LLM call. Reemplaza tool results identicos por referencias de una linea usando MD5.
- `smart_truncator.py` — extraccion estructurada por command type (test, build, lint, git, docker, json, generic).
- `context-budget-meter.sh` — telemetria de tokens por turno, loggeada en JSONL.
- `context_diet.py` — rules subset por task type: implement/review/debug/explore/test.

Ejemplo de impacto: un `./gradlew test` output de 80KB queda reducido a un resumen de errores + stack de los primeros 5 fallos (~3KB).

## Principio 2: Inter-agent communication is a protocol, not a free-for-all

Cada mensaje entre agentes debe declarar su context_mode (`full`/`summary`/`reference`/`none`) y schema_version. Los agentes que reciben saben que pueden esperar y los que mandan optimizan por tamano.

Mecanismos:

- `session_envelope.py` — contrato conceptual de transferencia de contexto con `schema_version` versionado.
- `agent_bus.py` — bus append-only con ack via `fcntl.flock`. CLI incluido.

Ejemplo de uso:

```python
from session_envelope import SessionEnvelope
from agent_bus import AgentBus
from pathlib import Path

bus = AgentBus(Path("out/agent-logs/agent-bus.jsonl"))

# El orchestrator manda contexto resumido al agente implementador
env = SessionEnvelope.create(
    sender="orchestrator",
    recipient="impl-agent",
    intent="Implementar endpoint POST /orders con validacion de stock",
    mode="summary",
    body="Stack: Java 21 + Quarkus. Reglas: clean-arch. Tests: ATDD con RestAssured.",
    corr_id="feature-orders-v2",
)
bus.send(env)

# El agente lee su inbox
msgs = bus.inbox("impl-agent")
bus.ack(msgs[0].message_id, status="processing")
```

## Principio 3: Sessions are first-class

Una sesion empieza con un boot que carga contexto, no con un prompt frio. Termina con un resumen de sesión que persiste lo aprendido.

Mecanismos:

- `session-bootstrap.sh` — hook UserPromptSubmit, carga anchor del ciclo anterior.
- `pre-compact-anchor.sh` — hook PreCompact, escribe `out/agent-state/anchor-<sessionid>.md` antes de que el contexto se comprima.
- `session-summary.sh` — hook Stop conceptual, genera un resumen privado de topics, files y stats.

Flujo:

```
Session N starts  -> session-bootstrap.sh reads anchor-N-1.md
Session N runs    -> mem_save decisions, skill routing logged
Context compacts  -> pre-compact-anchor.sh writes anchor-N.md
Session N ends    -> session summary persists learnings
Session N+1 starts-> session-bootstrap.sh reads anchor-N.md
```

## Principio 4: Primitives over context

No metas el contexto en el codebase. Metelo en primitivas (skills, rules, workflows) reusables. El contexto es volatile; las primitivas son source of truth.

Mecanismos:

- `.ai/primitives/` — fuente de verdad para skills, rules, workflows.
- `skill-router.py` + hook PreToolUse — invocacion automatica de skills relevantes.
- `workflow-runner.py` — orchestracion multi-step.
- `context_diet.py` + `context-diet.py` — seleccion minima de rules por task type.

## Principio 5: Every decision is observable

Cada decision que toma un agente (que skill aplicar, que rules cargar, cuanto contexto comprimir) deja rastro en `.ai/logs/`. La auditabilidad es no-negociable.

Mecanismos:

- `usage-stats.py` — agrega los logs de skill routing y workflows.
- `context-budget-meter.sh` — loggea token estimates por turno.
- `agent_bus.py` — bus JSONL append-only con ack records.
- Schema versioning en cada evento (`schema_version` field).

Todos los logs son JSONL append-only. No se sobreescriben, no se borran automaticamente.

## Principio 6: Caching is explicit

Aprovecha los caches que ofrece la API. Anthropic prompt cache da ~75% de descuento en re-runs con el mismo prefix estable.

Mecanismos:

- `prompt_cache.py` — `PromptCacheBuilder` que marca bloques estables con `cache_control: {type: ephemeral}`.

Patron de uso:

```python
from prompt_cache import PromptCacheBuilder

builder = PromptCacheBuilder()
builder.add_stable(SYSTEM_PROMPT)      # cache hit en re-runs
builder.add_stable(RULES_TEXT)         # idem
builder.add_volatile(user_message)     # NO cacheado (cambia por turno)

# Pasar a la API de Anthropic:
# client.messages.create(system=builder.build_system_blocks(), ...)
print(f"Stable chars: {builder.stable_char_count()}")
print(f"Volatile chars: {builder.volatile_char_count()}")
```

El `PromptCacheBuilder` respeta el limite de 4 breakpoints por request de la API de Anthropic. Si se excede, los bloques extras se degradan a volatile automaticamente.

## Principio 7: Token economy es responsabilidad del orchestrator

El orchestrator decide compression, dedup, references vs inline. No es tarea del LLM razonar sobre si repetir contexto es caro — eso es mecanico y debe ser automatizado.

Reglas de decision:

| Situacion | Decision |
|-----------|----------|
| Tool result > 4000 chars | `smart_truncator.py` antes de devolver |
| Tool result repetido | `context_pruner.prune_duplicates` -> stub |
| Tool results > 5 | `context_pruner.stub_old_tool_results(keep_last_n=5)` |
| Agente nuevo para tarea especifica | `context_diet.select_rules(task_type)` |
| Mensaje inter-agent | `HandoffEnvelope` con `mode="summary"` por defecto |

## Principio 8: IDE-agnostico es un objetivo

Todo lo de arriba se implementa con CLI tools que cualquier IDE puede invocar. Los hooks de Claude Code son un mecanismo conveniente, no la unica forma.

Portabilidad:

| Herramienta | Claude Code hook | Otro IDE |
|-------------|-----------------|----------|
| session-bootstrap | UserPromptSubmit | regla estatica o invocacion manual |
| pre-compact-anchor | PreCompact | checkpoint manual |
| session-summary | Stop | post-session manual |
| context-budget-meter | UserPromptSubmit | pre-prompt script |
| smart_truncator | PostToolUse (Bash) | wrapper de CLI |

TODO: cuando exista `.ai/research/ide-hooks-2026.md`, agregar aqui una referencia con la tabla de portabilidad completa por IDE (Cursor, Copilot, Zed, etc).

## Resumen de archivos del layer

```
.ai/lib/
  smart_truncator.py    — truncacion estructurada por command type
  context_pruner.py     — dedup por MD5 + stub de results antiguos
  context_diet.py       — rule bundles por task type
  prompt_cache.py       — PromptCacheBuilder para Anthropic API

.ai/scripts/
  agent_bus.py          — bus JSONL con flock + CLI
  context-diet.py       — CLI wrapper de context_diet
  pre-compact-anchor.sh — hook PreCompact
  context-budget-meter.sh — hook UserPromptSubmit (observer)

.ai/state/
  budgets.yaml          — limites de token por categoria
  anchor-template.md    — documentacion del formato anchor

.claude/settings.json   — hooks registrados
```

## Key Design Principle

> "Una arquitectura de agentes IA es tan exigente como una arquitectura de microservicios. La diferencia es que el bottleneck es el token, no el CPU. Los principios siguen siendo los mismos: contratos explicitos, observabilidad, isolation, caching."
