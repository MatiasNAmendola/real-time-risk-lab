# AGENTS.md — Real-Time Risk Lab

Entrypoint universal para agentes IA. Este archivo es leido automaticamente por: Codex CLI, opencode, Cursor, Kiro, y cualquier agente que siga la convencion AGENTS.md.

---

## 1. Quien sos

Este repo es una exploración técnica de un caso de uso de detección de fraude en tiempo real.

- Tema: 150 TPS sostenidos, p99 < 300ms, arquitectura híbrida sync (decisión) + async (audit, ML, downstream).
- Contexto de negocio: cada decisión de riesgo aprueba o rechaza una transacción de pago en < 300ms.
- Inspirado en patrones productivos de fraud detection (Lambda monolítico → EKS microservicios), pero no representa un sistema real ni código productivo.

---

## 2. Layout del repo

```
real-time-risk-lab/
├── poc/
│   ├── no-vertx-clean-engine/              # Sin Vert.x: Clean Architecture baseline
│   ├── vertx-monolith-inprocess/           # Con Vert.x: single JVM/in-process
│   ├── vertx-layer-as-pod-eventbus/        # Con Vert.x: layer-as-pod + clustered EventBus
│   ├── vertx-layer-as-pod-http/            # Con Vert.x: layer-as-pod + HTTP/tokens
│   ├── vertx-service-mesh-bounded-contexts/# Con Vert.x: bounded contexts service-to-service
│   └── k8s-local/               # k3d/OrbStack + ArgoCD + addons completos
├── tests/
│   └── risk-engine-atdd/        # Cucumber-JVM 7 ATDD
├── cli/
│   └── risk-smoke/              # Go + Bubble Tea TUI (9 smoke checks)
├── docs/                        # Documentación técnica (00-11)
├── .ai/                         # Sistema de primitivas IDE-agnosticas (este sistema)
├── AGENTS.md                    # Este archivo
└── CLAUDE.md                    # Entrypoint Claude Code
```

Arquitectura completa: [.ai/context/architecture.md](.ai/context/architecture.md)

---

## 3. PoCs

| PoC | Que demuestra | Como correr |
|---|---|---|
| `no-vertx-clean-engine` | Sin Vert.x: Clean Architecture pura, benchmarks | `./scripts/run.sh` |
| `vertx-monolith-inprocess` | Con Vert.x: single JVM/in-process, EventBus local | `./nx run vertx-monolith-inprocess` |
| `vertx-layer-as-pod-eventbus` | Con Vert.x: layer-as-pod vía clustered EventBus/Hazelcast | `./nx up vertx-layer-as-pod-eventbus && ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` |
| `vertx-layer-as-pod-http` | Con Vert.x: layer-as-pod vía HTTP + tokens | `./nx up vertx-layer-as-pod-http` |
| `vertx-service-mesh-bounded-contexts` | Con Vert.x: bounded contexts reales via EventBus RPC/async | `./scripts/up.sh && ./scripts/demo.sh` |
| `k8s-local` | ArgoCD, canary, SLO, AWS mocks | `./scripts/up.sh` |

Inventario completo: [.ai/context/poc-inventory.md](.ai/context/poc-inventory.md)

---

## 4. Reglas non-negotiable

Estas 5 reglas se aplican en TODO el codigo de este repo. No hay excepciones.

### R1: Java baseline real + objetivo LTS
Baseline ejecutable actual: **Java 21 LTS** (`--release 21`) por compatibilidad de Gradle/JMH/Karate/ArchUnit. Objetivo documentado: **Java 25 LTS** cuando el tooling soporte classfile 25 sin fricción. No afirmar Java 25 como build real si el repo compila con 21.
Regla completa: [.ai/primitives/rules/java-version.md](.ai/primitives/rules/java-version.md)

### R2: Layout enterprise Go en Java
Todo modulo Java sigue: `domain/{entity,repository,usecase,service,rule}`, `application/{usecase/<aggregate>,mapper,dto}`, `infrastructure/{controller,consumer,repository,resilience,time}`, `cmd/`, `config/`.
Regla completa: [.ai/primitives/rules/architecture-clean.md](.ai/primitives/rules/architecture-clean.md)

### R3: ATDD primero
Feature file antes de codigo de produccion. Karate 1.5+ o Cucumber-JVM 7+ segun contexto.
Regla completa: [.ai/primitives/rules/testing-atdd.md](.ai/primitives/rules/testing-atdd.md)

### R4: OTEL en todo request
Todo request produce trace + log estructurado + metrica. correlationId en MDC y en header de respuesta.
Regla completa: [.ai/primitives/rules/observability-otel.md](.ai/primitives/rules/observability-otel.md)

### R5: Clean boundaries
`domain/` NO importa de `application/` ni `infrastructure/`. Puertos en `domain/`, adapters en `infrastructure/`.
Regla completa: [.ai/primitives/rules/clean-arch-boundaries.md](.ai/primitives/rules/clean-arch-boundaries.md)

---

## 5. Como extender el sistema

Antes de implementar cualquier feature, busca el skill correspondiente:

```
.ai/primitives/skills/
  add-rest-endpoint.md       add-sse-stream.md          add-websocket-channel.md
  add-webhook-subscription.md add-kafka-publisher.md    add-kafka-consumer.md
  add-fraud-rule.md          add-port-out.md            add-port-in.md
  add-domain-entity.md       add-value-object.md        add-otel-custom-span.md
  add-otel-custom-metric.md  add-resilience-pattern.md  add-idempotency-key.md
  add-outbox-event.md        add-helm-template.md       add-prometheus-rule.md
  add-feature-test-karate.md add-feature-test-cucumber.md add-jacoco-coverage-target.md
  add-mock-aws-service.md    add-architecture-decision.md add-domain-entity.md
  bootstrap-new-poc.md       refactor-to-enterprise-layout.md benchmark-poc.md
  debug-failing-test.md      update-poc-readme.md       wire-engram-memory.md
  update-architecture-doc.md
```

Para tareas multi-paso, usa un workflow:
[.ai/primitives/workflows/](.ai/primitives/workflows/) — 8 workflows disponibles

---

## 6. Memoria persistente (Engram)

Este proyecto usa Engram MCP para memoria entre sesiones.

- Project key: `real-time-risk-lab`
- Al iniciar: `mem_context(project: "real-time-risk-lab")`
- Al finalizar: `mem_session_summary(...)` (OBLIGATORIO)
- Guia completa: [.ai/context/engram.md](.ai/context/engram.md)

---

## 7. Adapters por IDE

| IDE | Archivo principal | Adapter |
|---|---|---|
| Claude Code | `CLAUDE.md` | [.ai/adapters/claude-code/](.ai/adapters/claude-code/) |
| Cursor | `.cursor/rules/*.mdc` | [.ai/adapters/cursor/](.ai/adapters/cursor/) |
| Windsurf | `.windsurfrules` | [.ai/adapters/windsurf/](.ai/adapters/windsurf/) |
| GitHub Copilot | `.github/copilot-instructions.md` | [.ai/adapters/copilot/](.ai/adapters/copilot/) |
| Codex CLI | `AGENTS.md` (este archivo) | [.ai/adapters/codex/](.ai/adapters/codex/) |
| opencode | `AGENTS.md` + `.opencode/agents.md` | [.ai/adapters/opencode/](.ai/adapters/opencode/) |
| Kiro | `.kiro/instructions.md` | [.ai/adapters/kiro/](.ai/adapters/kiro/) |
| Antigravity | placeholder | [.ai/adapters/antigravity/](.ai/adapters/antigravity/) |

Para instalar todos los adapters:
```bash
for ide in claude-code cursor windsurf copilot codex opencode kiro; do
    ./.ai/adapters/$ide/install.sh
done
```

---

## 8. Estado de la exploración

Estado actual: [.ai/context/exploration-state.md](.ai/context/exploration-state.md)

---

> No edites codigo sin antes leer la rule aplicable y ejecutar el workflow correspondiente.
> Si te falta una primitiva, agregala en `.ai/primitives/` antes de implementar.
> Verifica el sistema: `.ai/scripts/verify-primitives.sh`
