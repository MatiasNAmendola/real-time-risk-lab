# Architecture — context pointer

> Este archivo es un pointer para agentes. La fuente de verdad atómica vive en `vault/`.

## Mapa rápido

- **Layout enterprise (R2)**: `vault/04-Concepts/Clean-Architecture.md` + `vault/04-Concepts/Hexagonal-Architecture.md`
- **Boundaries (R5)**: `.ai/primitives/rules/clean-arch-boundaries.md`
- **Screaming Architecture (R7)**: `.ai/primitives/rules/screaming-architecture.md` + `vault/04-Concepts/Screaming-Architecture.md`
- **Decisiones cerradas**: `vault/02-Decisions/` (ADRs, ver `_index.md`)
- **MOC raíz**: `vault/00-MOCs/Risk-Platform-Overview.md`
- **PoCs (tabla canónica)**: `AGENTS.md §3` o `docs/03-poc-roadmap.md`
- **Performance + paridad**: `vault/03-PoCs/Poc-Parity-Matrix.md`
- **Observabilidad**: `vault/02-Decisions/0045-observability-stack-local.md`
- **CQRS/Event Sourcing/EDA**: `docs/41-cqrs-event-sourcing-transacciones.md`

## Diagrama de alto nivel

```mermaid
graph TB
    subgraph repo["real-time-risk-lab/"]
        subgraph poc["poc/ — Proofs of Concept"]
            jre["no-vertx-clean-engine<br/>riskdecision.cleanengine<br/>bare-javac, Clean Arch"]
            jvd["vertx-layer-as-pod-eventbus<br/>riskdecision.layerpodeventbus<br/>4 módulos Gradle"]
            vrp["vertx-layer-as-pod-http<br/>riskdecision.layerpodhttp<br/>HTTP + tokens"]
            ntx["nestjs/hono transactional-risk<br/>CQRS/Event Sourcing simple<br/>Saga/TigerBeetle avanzada"]
            k8s["k8s-local<br/>k3d/OrbStack<br/>ArgoCD + addons"]
        end

        subgraph tests["tests/"]
            atdd["risk-engine-atdd<br/>Cucumber-JVM 7<br/>ATDD sobre API HTTP"]
        end

        subgraph cli["cli/"]
            smoke["risk-smoke<br/>Go + Bubble Tea TUI<br/>9 smoke checks"]
        end

        subgraph ai[".ai/ — primitives system"]
            prim["primitives/<br/>skills/ rules/ workflows/ hooks/"]
            ctx["context/<br/>architecture poc-inventory decisions glossary"]
            adp["adapters/<br/>claude-code cursor windsurf copilot codex opencode kiro antigravity"]
        end
    end

    smoke -->|HTTP/WS/SSE/Kafka| vrp
    smoke -->|HTTP| jvd
    atdd -->|HTTP| jre
    k8s -->|deploys| vrp
```

## Flujo de una transacción

```
Cliente
  → POST /risk (REST)
    → correlationId generado
    → RiskHandler (infrastructure/controller)
      → EvaluateTransactionUseCase (application/usecase)
        → RuleEngine.evaluate() (domain/rule)
          → Reglas deterministicas (velocidad, monto, comercio)
          → ML scoring (circuit breaker, fallback)
        → TransactionRepository.save() (infrastructure/repository → Postgres)
        → IdempotencyStore.put() (infrastructure/repository → Valkey)
        → EventPublisher.publish() (infrastructure/publisher → Tansu)
    → RiskDecision response + X-Correlation-Id header
  → OTEL span cerrado
  → Log estructurado con correlationId, traceId
```

## Reglas non-negotiable (R1-R5)

Definidas en `AGENTS.md §4` y `.ai/primitives/rules/`.
