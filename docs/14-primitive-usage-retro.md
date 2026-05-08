---
title: "14 — Primitive Usage Retro: Real vs. Ideal"
date: 2026-05-07
session: prep-day
scope: post-mortem
---

# Primitive Usage Retro — 2026-05-07 Prep Day

## 1. Inventario de primitivas

### Skills (30)

| Skill | Intent resumido |
|---|---|
| `add-architecture-decision` | Registrar ADR en decisions-log.md y Engram |
| `add-domain-entity` | Agregar entidad de dominio con invariantes |
| `add-feature-test-cucumber` | Agregar feature test Cucumber-JVM en tests/risk-engine-atdd/ |
| `add-feature-test-karate` | Agregar feature test Karate sobre endpoint Vert.x |
| `add-fraud-rule` | Agregar regla deterministica al motor de decisiones |
| `add-helm-template` | Agregar recurso Kubernetes como template Helm |
| `add-idempotency-key` | Agregar soporte de idempotencia a use case o endpoint |
| `add-jacoco-coverage-target` | Agregar/actualizar target JaCoCo en modulo Gradle |
| `add-kafka-consumer` | Agregar consumidor Kafka/Redpanda para eventos de dominio |
| `add-kafka-publisher` | Agregar publicacion de eventos de dominio a Redpanda/Kafka |
| `add-mock-aws-service` | Agregar servicio AWS mockeado localmente |
| `add-otel-custom-metric` | Agregar metrica custom OpenTelemetry (counter/histogram/gauge) |
| `add-otel-custom-span` | Agregar span OTEL custom para operacion de negocio |
| `add-outbox-event` | Implementar Outbox Pattern para consistencia eventual |
| `add-port-in` | Agregar puerto de entrada (driving port) |
| `add-port-out` | Agregar puerto de salida (driven port) con adapter |
| `add-prometheus-rule` | Agregar PrometheusRule con alertas SLI/SLO |
| `add-resilience-pattern` | Agregar circuit breaker / bulkhead / retry / timeout |
| `add-rest-endpoint` | Agregar endpoint REST respetando OpenAPI y Clean Architecture |
| `add-sse-stream` | Agregar endpoint SSE para streaming de decisiones |
| `add-value-object` | Agregar Value Object inmutable con validacion |
| `add-webhook-subscription` | Agregar registro y entrega de webhooks |
| `add-websocket-channel` | Agregar canal WebSocket bidireccional |
| `benchmark-poc` | Ejecutar benchmark de latencia/throughput y reportar percentiles |
| `bootstrap-new-poc` | Arrancar nueva PoC con convenciones del repo |
| `debug-failing-test` | Diagnosticar y resolver test ATDD o unitario fallando |
| `refactor-to-enterprise-layout` | Refactorizar modulo Java al layout canonico enterprise Go |
| `update-architecture-doc` | Update architecture document or project state |
| `update-poc-readme` | Actualizar README de PoC con estado actual |
| `wire-engram-memory` | Cargar/guardar contexto en Engram al inicio/fin de sesion |

### Rules (12)

`architecture-clean`, `clean-arch-boundaries`, `communication-patterns`, `containers-docker`, `error-handling`, `events-versioning`, `java-version`, `licensing`, `naming-conventions`, `observability-otel`, `secrets-handling`, `testing-atdd`

### Workflows (8)

`add-comm-pattern`, `debug-trace-issue`, `deploy-to-k8s-local`, `architecture-review-checklist`, `new-feature-atdd`, `new-poc-bootstrap`, `release-poc-version`, `wire-new-ide`

### Hooks (5)

`post-tool-use-test`, `pre-commit-format`, `pre-tool-use-block-secrets`, `session-end-engram-save`, `session-start-engram-load`

---

## 2. Acciones reales de la sesion (reconstruidas desde vault y artefactos)

Fuente: artefactos en `poc/`, `tests/`, `docs/`, `cli/`, `bench/`, `.ai/`.

1. Analyze production-inspired fraud detection use case requirements (Transactional Risk, 150 TPS, p99 < 300ms)
2. Producir docs 00-04 (mapa tecnico, design framework, discovery questions, roadmap, clean arch)
3. Construir PoC bare-javac `poc/no-vertx-clean-engine/` con Clean Architecture sin framework
4. Agregar HTTP controller adapter a `no-vertx-clean-engine`
5. Agregar Circuit Breaker manual a `no-vertx-clean-engine`
6. Agregar Idempotency guard a `no-vertx-clean-engine`
7. Agregar Outbox Pattern stub a `no-vertx-clean-engine`
8. Refactorizar `no-vertx-clean-engine` al layout enterprise Go
9. Producir docs 05-09 (latency budget, eventos versionados, lambda vs EKS, ML online, architecture question bank)
10. Investigacion profunda de patrones enterprise Go
11. Construir PoC Vert.x distribuida `poc/vertx-layer-as-pod-eventbus/` (4 modulos Gradle, Hazelcast, 4 redes Docker)
12. Sumar todos los patrones de comunicacion a Vert.x: REST, SSE, WebSocket, Webhooks, Kafka publisher
13. Sumar OTEL completo a Vert.x (MDC correlationId, custom spans, Micrometer metrics, OpenObserve)
14. Construir `poc/k8s-local/` con k3d + ArgoCD + Argo Rollouts + kube-prom-stack + Redpanda + OpenObserve
15. Agregar OrbStack/k3d autodetect switch a `k8s-local`
16. Agregar AWS mocks addon a `k8s-local` (Moto, MinIO, ElasticMQ, OpenBao)
17. Construir Go TUI smoke runner `cli/risk-smoke/` con Bubble Tea (9 E2E checks)
18. Construir ATDD Karate `poc/vertx-layer-as-pod-eventbus/atdd-tests/` (10 features Gherkin, JaCoCo cross-module)
19. Construir ATDD Cucumber-JVM `tests/risk-engine-atdd/` (7 features Gherkin)
20. Producir doc 10 AWS mocks locales
21. Producir doc 11 ATDD
22. Construir benchmark comparativo `bench/` (inprocess vs distributed)
23. Construir ArchUnit verification `tests/architecture/` (BareJavacArchitectureTest, VertxDistributedArchitectureTest)
24. Construir `tests/integration/` con testcontainers
25. Producir doc 12 rendimiento y separacion arquitectonica
26. Disenar sistema `.ai/` con 30 skills, 12 rules, 8 workflows, 5 hooks
27. Construir `skill-router.py` CLI que rankea skills por query
28. Construir adapters por IDE (cursor, claude-code, copilot, codex, kiro, windsurf, opencode, antigravity)
29. Instalar 30 sub-agents en `.claude/agents/`
30. Producir `AGENTS.md`, `CLAUDE.md`, `.cursor/rules/*.mdc`
31. Construir Obsidian vault con MOCs, ADRs (11), concept notes, session log
32. Sumar reporting layer a smoke + Karate + Cucumber

---

## 3. Mapeo: accion real vs. primitive esperado

| # | Accion | Skill / Workflow / Rule esperado | Se invoco | Evidencia |
|---|---|---|---|---|
| 1 | Producir docs 00-04 | `update-architecture-doc` | NO | Docs generados directamente sin referenciar el skill |
| 2 | Construir PoC bare-javac | `bootstrap-new-poc` + workflow `new-poc-bootstrap` | NO | PoC creada ad-hoc; workflow `new-poc-bootstrap` existe pero no fue invocado |
| 3 | Agregar HTTP controller | `add-rest-endpoint` | NO | El agente recibio prompt directo sin citar `.ai/primitives/skills/add-rest-endpoint.md` |
| 4 | Agregar Circuit Breaker | `add-resilience-pattern` | NO | Implementacion ad-hoc; rule `error-handling` no fue citada |
| 5 | Agregar Idempotency guard | `add-idempotency-key` | NO | Prompt de agente no referencio el skill |
| 6 | Agregar Outbox stub | `add-outbox-event` | NO | Skill existe, no invocado |
| 7 | Refactorizar a enterprise Go layout | `refactor-to-enterprise-layout` | NO | Skill existe exactamente para esto; no fue invocado |
| 8 | Construir Vert.x PoC | `bootstrap-new-poc` + workflow `new-poc-bootstrap` | NO | Segunda PoC creada sin workflow; mismas convenciones replicadas manualmente |
| 9 | Sumar REST a Vert.x | `add-rest-endpoint` + workflow `add-comm-pattern` | NO | Workflow `add-comm-pattern` cubre exactamente este caso; ignorado |
| 10 | Sumar SSE a Vert.x | `add-sse-stream` + workflow `add-comm-pattern` | NO | idem |
| 11 | Sumar WebSocket a Vert.x | `add-websocket-channel` + workflow `add-comm-pattern` | NO | idem |
| 12 | Sumar Webhooks a Vert.x | `add-webhook-subscription` + workflow `add-comm-pattern` | NO | idem |
| 13 | Sumar Kafka publisher a Vert.x | `add-kafka-publisher` + workflow `add-comm-pattern` | NO | idem |
| 14 | Sumar OTEL custom spans | `add-otel-custom-span` | NO | Rule `observability-otel` aplica; no fue citada en el prompt |
| 15 | Sumar OTEL custom metrics | `add-otel-custom-metric` | NO | idem |
| 16 | Construir k8s-local | primitive no existe (gap: no hay skill `bootstrap-k8s-poc`) | N/A | El skill mas cercano es `add-helm-template`; no cubre la inicializacion |
| 17 | Agregar OrbStack/k3d switch | primitive no existe (gap) | N/A | No hay skill para autodeteccion de proveedor k8s local |
| 18 | Agregar AWS mocks | `add-mock-aws-service` | NO | Skill existe y es exacto; no invocado |
| 19 | Construir Go TUI smoke | primitive no existe (gap: no hay skill para Go CLI) | N/A | El sistema de skills es Java-centric |
| 20 | Construir ATDD Karate | `add-feature-test-karate` + workflow `new-feature-atdd` | NO | Workflow `new-feature-atdd` existe; no invocado |
| 21 | Construir ATDD Cucumber | `add-feature-test-cucumber` + workflow `new-feature-atdd` | NO | idem |
| 22 | Construir benchmark | `benchmark-poc` | NO | Skill existe; no invocado |
| 23 | Construir ArchUnit tests | primitive no existe (gap: no hay skill `add-arch-test`) | N/A | ArchUnit es transversal; no modelado como skill |
| 24 | Agregar JaCoCo coverage | `add-jacoco-coverage-target` | NO | Skill existe; no invocado |
| 25 | Construir testcontainers | primitive no existe (gap: no hay skill `add-integration-test`) | N/A | Solo ATDD esta modelado |
| 26 | Registrar ADRs (11) | `add-architecture-decision` | NO | ADRs escritos directamente en vault y context/decisions-log.md sin invocar el skill |
| 27 | Sumar reporting layer | primitive no existe (gap: no hay skill `add-test-reporting`) | N/A | Reporting no esta modelado como primitiva |
| 28 | Construir sistema .ai/ | `wire-new-ide` + workflow `wire-new-ide` | NO | El sistema fue construido desde cero; el workflow `wire-new-ide` fue definido durante (o despues de) este paso, no como guia |
| 29 | Cargar/guardar contexto Engram | `wire-engram-memory` + hook `session-start-engram-load` | PARCIAL | Hook `session-start-engram-load` fue definido pero no ejecutado por el harness (`.claude/settings.json` solo tiene un echo stub, no invoca el hook real) |
| 30 | Pre-commit / secrets check | hook `pre-tool-use-block-secrets` | NO | Settings.json tiene el matcher `Edit|Write` pero el comando es `echo '...'`, no el script real |
| 31 | Post-tool test runner | hook `post-tool-use-test` | NO | No esta en `settings.json` |
| 32 | Producir doc 12 rendimiento | `update-architecture-doc` | NO | Documento generado sin invocar el skill |

**Resumen de la tabla**:
- Total acciones auditadas: 32
- Primitivas NO invocadas cuando existia la primitiva adecuada: 25
- Primitivas invocadas: 0 (la fila "PARCIAL" es el hook de engram; el comando es un stub sin efecto real)
- Casos sin primitiva equivalente (gaps): 6
- `skill-router.py` invocado para cualquier decision: 0 veces

---

## 4. Por que no se usaron

### a) Las primitivas se construyeron despues de (o en paralelo con) la mayoria de las acciones

La sesion siguio el orden cronologico documentado en el blow-by-blow: primero las PoCs y docs (pasos 1-15), luego el sistema `.ai/` (paso 16-17). Las primitivas modelan tareas que ya estaban terminadas o en curso cuando fueron escritas. No existian para guiar; fueron escritas para describir lo que se habia hecho.

### b) Los agentes posteriores a la construccion del sistema .ai/ tampoco las invocaron

Los pasos 18-32 (reporting, ArchUnit, testcontainers, benchmark) se ejecutaron mientras el sistema `.ai/` ya existia. Aun asi, ningun prompt de sub-agente cito `SKILL: Load .ai/primitives/skills/debug-failing-test.md`. El orquestador replic su patron previo de prompts auto-contenidos.

### c) El skill-router existe pero nunca se llamo como paso previo

`.ai/scripts/skill-router.py` esta construido, probado (`test_skill_router.py` existe), documentado (`README-router.md` existe). No fue invocado como paso de pre-dispatch en ningun lanzamiento de agente. El mecanismo existe; el habito no.

### d) Los hooks estan declarados como documentos, no como comandos ejecutables

El unico hook wired en `.claude/settings.json` corre `echo 'Pre-tool hook: secrets check would run here'`. Los cinco hooks en `.ai/primitives/hooks/` son specs de intencion, no comandos reales. La brecha entre documento y ejecucion no fue cerrada.

### e) El orquestador tiene contexto historico del como construyo la primera version

Claude Code replic el proceso de construccion inicial en cada agente subsiguiente: prompt directo con descripcion de tarea, sin lookup previo en `.ai/`. No hay penalizacion por no usar las primitivas; no hay recompensa visible por usarlas. El default gana.

---

## 5. Como evidenciar uso correcto en la proxima sesion

### 1. Pre-tool-use hook con skill-router real

Reemplazar el echo stub en `.claude/settings.json` por:

```json
{
  "matcher": "Edit|Write",
  "hooks": [
    {
      "type": "command",
      "command": "python3 .ai/scripts/skill-router.py \"$CLAUDE_TOOL_INPUT_description\" 2>/dev/null >> out/agent-logs/skill-routing-$(date +%Y%m%d).log || true"
    }
  ]
}
```

Esto produce `out/agent-logs/skill-routing-YYYYMMDD.log` con los top-3 skills relevantes para cada Edit/Write. Al final de la sesion, ese log es la evidencia auditeable de uso.

### 2. Prompt prefix obligatorio en CLAUDE.md

Agregar en la seccion "Sub-agents disponibles":

> ANTES de lanzar un agente para cualquier tarea de implementacion, ejecutar:
> `python3 .ai/scripts/skill-router.py "<descripcion de la tarea>"` y citar el skill con mayor score en el prompt del agente como `SKILL: Load .ai/primitives/skills/<name>.md`. Si el score es < 0.5, documentar por que no aplica ninguna primitiva.

Esto convierte el skill-router de herramienta opcional a paso de orquestacion obligatorio.

### 3. Workflow dispatcher para tareas multi-step

Para cualquier tarea que involucre mas de un skill (ejemplo: agregar un nuevo patron de comunicacion implica `add-rest-endpoint` o `add-kafka-publisher` + `add-otel-custom-span` + `add-feature-test-karate`), invocar primero el workflow correspondiente (`add-comm-pattern`, `new-feature-atdd`) y seguir sus pasos en orden documentado, no improvisar la secuencia.

### 4. Retro de uso al final de cada sesion (este documento como template)

Al cerrar cada sesion, generar un retro con la misma estructura: inventario, acciones, mapeo, analisis, metricas. Engram persiste el resultado bajo topic-key `primitive-usage-retro/<fecha>`. La serie de retros muestra tendencia de adopcion.

### 5. Skill router como MCP server

Exponer `skill-router.py` como MCP tool server para que Claude Code pueda invocarlo sin Bash, con tipos y autocompletado. Esto elimina la friccion de recordar la CLI y permite que el orquestador lo llame programaticamente en cada decision de dispatch.

---

## 6. Metricas para la proxima sesion

| Metrica | Objetivo | Como medir |
|---|---|---|
| % de tasks que invocaron skill-router antes del dispatch | > 80% | Contar lineas en `out/agent-logs/skill-routing-<sesion>.log` vs. total de sub-agentes lanzados |
| % de prompts a sub-agentes que citan skill o workflow | > 70% | Grep `SKILL: Load` o `WORKFLOW: Load` en transcripcion de sesion |
| Gaps de primitiva identificados | Tender a 0 (cubrir los 6 detectados hoy) | Contar filas "gap" en la tabla del retro siguiente |
| Hooks activos (comandos reales, no echo) | 5/5 hooks wired | `cat .claude/settings.json | grep -c command` y verificar que ninguno es solo echo |
| Tiempo entre user request y first useful tool call | Reduccion vs sesion anterior | Timestamp en transcripcion; si el primer tool call es `skill-router.py`, el sistema esta funcionando |

---

## 7. Key Design Principle

> "Las primitivas las construimos. Su uso real es la metrica que importa. La proxima sesion empieza con `skill-router.py` antes que con cualquier Edit. Eso es la diferencia entre tener un sistema y operarlo."

---

## 8. Conclusion honesta

En esta sesion, las primitivas fueron construidas con rigor: 30 skills atomicos, 12 rules, 8 workflows, 5 hooks, un skill-router funcional, adapters para 8 IDEs. Cero de esas primitivas guiaron activamente una accion de construccion. La orquestacion fue ad-hoc de principio a fin: el orquestador despacho prompts directos a sub-agentes sin un paso previo de lookup en `.ai/`. El unico hook wired es un echo stub. El skill-router tiene tests y documentacion pero no fue llamado ni una sola vez durante el trabajo real. El sistema existe en estado de latencia.

El gap entre tener primitivas y usarlas es exactamente la deuda operacional que cualquier sistema de agentes IA acumula en 2026. Las primitivas se escriben cuando ya sabes que hiciste; se usan cuando el mecanismo de invocacion es mas barato que improvisar. Los cinco mecanismos propuestos en la seccion 5 buscan cerrar esa brecha: hook con log auditeable, prefix obligatorio en CLAUDE.md, workflow dispatcher, retro periodico, MCP server. El sistema esta cableado para que la proxima sesion si use las primitivas. La pregunta es si el habito precede al resultado o si el resultado crea el habito. Esta vez construimos el sistema antes del habito. La proxima sesion mide si el habito llego.

---

## 9. Implementation status

| Mechanism | Status | Evidence |
|---|---|---|
| Pre-tool-use hook calls skill-router | IMPLEMENTED | `.claude/settings.json` hooks.PreToolUse |
| Skill router invoked from hook | IMPLEMENTED | `.ai/scripts/skill-route-from-tool.sh` |
| Workflow dispatcher | IMPLEMENTED | `.ai/scripts/workflow-runner.py` |
| Mandatory prompt prefix in CLAUDE.md | IMPLEMENTED | CLAUDE.md "Primitive-first protocol" section |
| Usage telemetry | IMPLEMENTED | `.ai/scripts/usage-stats.py`, logs in `.ai/logs/` |
| Session bootstrap | IMPLEMENTED | `.ai/scripts/session-bootstrap.sh` |

Las primitivas las construimos, los hooks las invocan automaticamente, la telemetria evidencia su uso. Esa es la diferencia entre tener un sistema y operarlo.
