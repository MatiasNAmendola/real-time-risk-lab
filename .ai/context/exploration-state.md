# Project State — Real-Time Risk Lab

> Estado actual estable del proyecto. Las entradas datadas (retros, milestones, descubrimientos) viven en Engram.

**Project**: Real-Time Risk Lab — multi-architecture exploration
**Domain**: Production-grade fraud detection use case

---

## General Status

| Area | Status | Notes |
|---|---|---|
| Technical docs + vault | En consolidación | docs actuales + vault Obsidian; `docs/42-documentacion-metodica.md` define la metodología |
| PoC no-vertx-clean-engine | Complete | Clean Arch, benchmarks, idempotency |
| PoC vertx-layer-as-pod-eventbus | Stable | 4 modules, partial ATDD |
| PoC k8s-local | Stable | ArgoCD, canary, SLO, AWS mocks |
| PoC vertx-layer-as-pod-http | In progress | REST working, extension in progress |
| CLI risk-smoke (Go TUI) | In progress | 9 checks, partially functional |
| ATDD Karate (in PoC) | In progress | Some scenarios, needs completion |
| ATDD Cucumber (tests/) | In progress | Structure ready, scenarios in development |
| .ai/ primitives system | Completed | skills/rules/workflows/hooks + adapters; incluye regla `documentation-system` |
| PoC nestjs-distributed-transactions | Implementado | CQRS/Event Sourcing simple de cuentas; Saga/TigerBeetle/EDA avanzada |
| PoC hono-distributed-transactions | Implementado | Contraste minimalista Hono del mismo recorrido simple/avanzado |

---

## Completed

- [x] docs/00-START-HERE.md
- [x] docs/03-poc-roadmap.md
- [x] docs/15-mapa-tecnico.md
- [x] docs/27-test-runner.md
- [x] docs/40-bun-package-manager-security.md
- [x] docs/41-cqrs-event-sourcing-transacciones.md
- [x] docs/42-documentacion-metodica.md
- [x] vault/05-Methodology/Architecture-Question-Bank.md
- [x] vault/04-Concepts/ATDD.md
- [x] vault/03-PoCs/Poc-Parity-Matrix.md
- [x] poc/no-vertx-clean-engine: Clean Architecture, functional engine, benchmarks
- [x] poc/vertx-layer-as-pod-eventbus: 4 Gradle modules, docker-compose, Hazelcast
- [x] poc/k8s-local: ArgoCD, Argo Rollouts canary, kube-prom, ESO, Tansu, OpenObserve, AWS mocks
- [x] .ai/ system with IDE-agnostic primitives
- [x] poc/nestjs-distributed-transactions: NestJS 11, compensaciones de saga, puerto TigerBeetle, CQRS/Event Sourcing, idempotencia EDA con BullMQ/Valkey
- [x] poc/hono-distributed-transactions: contraste Hono/Zod/wiring manual para el mismo set de patrones

---

## In Progress

- [ ] poc/vertx-layer-as-pod-http: extend with SSE, WebSocket, Webhook, Kafka consumer, AsyncAPI
- [ ] cli/risk-smoke: complete the 9 checks (some may be failing)
- [ ] ATDD Karate: complete happy path + error case scenarios
- [ ] ATDD Cucumber: add more scenarios to tests/risk-engine-atdd/
- [ ] Benchmarks in vertx-layer-as-pod-http: p50/p95/p99 documented

---

## Pending (if time allows)

- [ ] ML scoring integration demo (docs/08-ml-online.md)
- [ ] Profundizar k6 a escenarios sostenidos de 150 TPS sobre las PoCs principales
- [ ] Incident response runbook in OpenObserve

---

## Immediate Priorities

1. Verify that all PoCs compile and run.
2. Complete at least 1 green ATDD scenario for each PoC.
3. Verify that k8s-local starts without errors.
4. Review `vault/05-Methodology/Architecture-Question-Bank.md` and work through model analyses.
5. Prepare terminal with demo commands ready.

---

## Quick Verification Commands

```bash
# PoC 1
cd poc/no-vertx-clean-engine && ./scripts/run.sh

# PoC 2
./nx up vertx-layer-as-pod-eventbus && ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd

# PoC 3
cd poc/k8s-local && ./scripts/up.sh && ./scripts/status.sh

# CLI smoke
cd cli/risk-smoke && go run .
```

---

## Key Technical Strengths

1. Clean Architecture demonstrated with real code (not just slides).
2. OTEL observability end-to-end: trace from HTTP through Kafka.
3. Real infrastructure knowledge: ArgoCD, canary, SLO, AWS stack.
4. ATDD as a practice: feature file before implementation.
5. Explicit trade-offs: Vert.x vs Spring Boot, Redpanda vs Kafka, etc.

## Areas to Request Thinking Time For

- Hazelcast configuration details in production at scale.
- Kafka partition sharding optimization for 150 TPS.
- Internal details of how IRSA works.

---

## Historial datado

Las entradas con fecha (retros, milestones, descubrimientos) ahora viven en Engram. Buscá:

- `mem_search(query: "exploration-state", project: "real-time-risk-lab")`
- O por fecha: `mem_search(query: "exploration-state-2026-05")`

## Próximos pasos — TypeScript Distributed Transactions

- Si se agregan nuevos módulos NestJS, mantener controllers/DTOs/handlers en `src/internal/infrastructure`.
- Si se agregan nuevos use cases, deben depender sólo de puertos propios del dominio/aplicación.
- Guardrails específicos agregados: `poc/nestjs-distributed-transactions/scripts/check-boundaries.sh`, `poc/hono-distributed-transactions/scripts/check-boundaries.sh`, ESLint `no-restricted-imports` y `.ai/scripts/quick-check.py`.

- Si querés profundizar TigerBeetle real: reemplazar el fallback por llamadas completas a cuentas/transfers del cluster.
- Agregar ATDD HTTP estilo Karate/Cucumber para la nueva PoC si se quiere demo end-to-end externa.
- Agregar smoke check al CLI Go si esta PoC debe entrar en la demo principal.
