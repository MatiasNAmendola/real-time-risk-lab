# AGENTS.md — Risk Decision Platform

Entrypoint universal para agentes IA. Este archivo es leido automaticamente por: Codex CLI, opencode, Cursor, Kiro, y cualquier agente que siga la convencion AGENTS.md.

---

## 1. Quien sos

Este repo es una exploración técnica de un caso de uso de detección de fraude en tiempo real.

- Tema: 150 TPS sostenidos, p99 < 300ms, arquitectura híbrida sync (decisión) + async (audit, ML, downstream).
- Contexto de negocio: cada decisión de riesgo aprueba o rechaza una transacción de pago en < 300ms.
- Inspirado en un caso de uso productivo de fraud detection (Lambda monolítico → EKS microservicios).

---

## 2. Layout del repo

```
practica-entrevista/
├── poc/
│   ├── java-risk-engine/        # Clean Architecture sin frameworks (bare-javac)
│   ├── java-vertx-distributed/  # Vert.x 5, 4 modulos Maven, layer-as-pod
│   ├── vertx-risk-platform/     # Plataforma Vert.x completa (todos los patrones)
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
| `java-risk-engine` | Clean Architecture pura, benchmarks | `./scripts/run.sh` |
| `java-vertx-distributed` | Arquitectura distribuida Vert.x | `docker-compose up && mvn test -pl atdd-tests` |
| `vertx-risk-platform` | REST+SSE+WS+Webhook+Kafka+OTEL | `mvn package && ./scripts/run.sh` |
| `k8s-local` | ArgoCD, canary, SLO, AWS mocks | `./scripts/up.sh` |

Inventario completo: [.ai/context/poc-inventory.md](.ai/context/poc-inventory.md)

---

## 4. Reglas non-negotiable

Estas 5 reglas se aplican en TODO el codigo de este repo. No hay excepciones.

### R1: Java 25 LTS
Java 25 canonico. NO bajar a 21. NO subir a 26 (no es LTS).
`<maven.compiler.release>25</maven.compiler.release>` en todo pom.xml.
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

- Project key: `risk-decision-platform`
- Al iniciar: `mem_context(project: "risk-decision-platform")`
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
