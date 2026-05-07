# Project State — Risk Decision Platform

**Project**: Risk Decision Platform — three-architecture exploration
**Domain**: Production-grade fraud detection use case
**Last updated**: 2026-05-07

---

## General Status

| Area | Status | Notes |
|---|---|---|
| Technical docs (docs/00-11) | Complete | 12 documents ready |
| PoC java-risk-engine | Complete | Clean Arch, benchmarks, idempotency |
| PoC java-vertx-distributed | Stable | 4 modules, partial ATDD |
| PoC k8s-local | Stable | ArgoCD, canary, SLO, AWS mocks |
| PoC vertx-risk-platform | In progress | REST working, extension in progress |
| CLI risk-smoke (Go TUI) | In progress | 9 checks, partially functional |
| ATDD Karate (in PoC) | In progress | Some scenarios, needs completion |
| ATDD Cucumber (tests/) | In progress | Structure ready, scenarios in development |
| .ai/ primitives system | Completed | 30 skills, 12 rules, 8 workflows, 5 hooks |

---

## Completed

- [x] docs/00-mapa-tecnico.md
- [x] docs/01-design-conversation-framework.md
- [x] docs/02-platform-discovery-questions.md
- [x] docs/03-poc-roadmap.md
- [x] docs/04-clean-architecture-java.md
- [x] docs/05-budget-y-bottlenecks.md
- [x] docs/06-eventos-versionados.md
- [x] docs/07-lambda-vs-eks.md
- [x] docs/08-ml-online.md
- [x] docs/09-architecture-question-bank.md
- [x] docs/10-aws-mocks-locales.md
- [x] docs/11-atdd.md
- [x] poc/java-risk-engine: Clean Architecture, functional engine, benchmarks
- [x] poc/java-vertx-distributed: 4 Maven modules, docker-compose, Hazelcast
- [x] poc/k8s-local: ArgoCD, Argo Rollouts canary, kube-prom, ESO, Redpanda, OpenObserve, AWS mocks
- [x] .ai/ system with IDE-agnostic primitives

---

## In Progress

- [ ] poc/vertx-risk-platform: extend with SSE, WebSocket, Webhook, Kafka consumer, AsyncAPI
- [ ] cli/risk-smoke: complete the 9 checks (some may be failing)
- [ ] ATDD Karate: complete happy path + error case scenarios
- [ ] ATDD Cucumber: add more scenarios to tests/risk-engine-atdd/
- [ ] Benchmarks in vertx-risk-platform: p50/p95/p99 documented

---

## Pending (if time allows)

- [ ] ML scoring integration demo (docs/08-ml-online.md)
- [ ] CQRS demo in a separate PoC
- [ ] Load test with k6 showing 150 TPS
- [ ] Incident response runbook in OpenObserve

---

## Immediate Priorities

1. Verify that all PoCs compile and run.
2. Complete at least 1 green ATDD scenario for each PoC.
3. Verify that k8s-local starts without errors.
4. Review docs/09-architecture-question-bank.md and work through model analyses.
5. Prepare terminal with demo commands ready.

---

## Quick Verification Commands

```bash
# PoC 1
cd poc/java-risk-engine && ./scripts/run.sh

# PoC 2
cd poc/java-vertx-distributed && docker-compose up -d && mvn test -pl atdd-tests

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
