# AGENTS.md — Real-Time Risk Lab

Universal entrypoint for AI agents. This file is read automatically by Codex CLI, opencode, Cursor, Kiro, and any agent that follows the `AGENTS.md` convention.

---

## 1. Identity

This repository is a technical exploration of real-time fraud detection architecture.

- Topic: 150 sustained TPS, p99 < 300 ms, hybrid sync decision + async audit/ML/downstream architecture.
- Business context: each risk decision approves or rejects a payment transaction in < 300 ms.
- Inspired by production fraud-detection patterns, but this is not a real production system.

---

## 2. Repository layout

```text
real-time-risk-lab/
├── poc/
│   ├── no-vertx-clean-engine/              # No Vert.x: Clean Architecture baseline
│   ├── vertx-monolith-inprocess/           # Vert.x: single JVM/in-process
│   ├── vertx-layer-as-pod-eventbus/        # Vert.x: layer-as-pod + clustered EventBus
│   ├── vertx-layer-as-pod-http/            # Vert.x: layer-as-pod + HTTP/tokens
│   ├── vertx-service-mesh-bounded-contexts/# Vert.x: real bounded contexts service-to-service
│   └── k8s-local/                          # k3d/OrbStack + ArgoCD + addons
├── tests/
│   └── risk-engine-atdd/                   # Cucumber-JVM 7 ATDD
├── cli/
│   └── risk-smoke/                         # Go + Bubble Tea TUI smoke checks
├── docs/                                   # Technical documentation
├── vault/                                  # Obsidian vault: ADRs, concepts, PoCs, methodology
├── .ai/                                    # IDE-agnostic primitive system
├── AGENTS.md                               # Universal agent entrypoint
└── CLAUDE.md                               # Claude Code entrypoint
```

Full architecture: `.ai/context/architecture.md`
PoC inventory: `.ai/context/poc-inventory.md`

---

## 3. PoCs

| PoC | Demonstrates | Run |
|---|---|---|
| `no-vertx-clean-engine` | Pure Clean Architecture baseline, benchmarks | `./scripts/run.sh` |
| `vertx-monolith-inprocess` | Vert.x single JVM/in-process, local EventBus | `./nx run vertx-monolith-inprocess` |
| `vertx-layer-as-pod-eventbus` | Layer-as-pod with clustered EventBus/Hazelcast | `./nx up vertx-layer-as-pod-eventbus && ./gradlew :poc:vertx-layer-as-pod-eventbus:atdd-tests:test -Patdd` |
| `vertx-layer-as-pod-http` | Layer-as-pod through HTTP + tokens | `./nx up vertx-layer-as-pod-http` |
| `vertx-service-mesh-bounded-contexts` | Real bounded contexts through EventBus RPC/async | `./scripts/up.sh && ./scripts/demo.sh` |
| `k8s-local` | ArgoCD, canary, SLOs, AWS mocks | `./scripts/up.sh` |
| `nestjs-distributed-transactions` | CQRS/Event Sourcing + Saga/TigerBeetle/BullMQ | `bun run start` from the PoC dir |
| `hono-distributed-transactions` | Same transactional demo with Hono/manual wiring | `bun run start` from the PoC dir |

---

## 4. Non-negotiable rules

These rules apply to the entire repository.

### R1: Real Java baseline + LTS target

Executable baseline: **Java 21 LTS** (`--release 21`) for Gradle/JMH/Karate/ArchUnit compatibility.
Documented target: **Java 25 LTS** when tooling supports it without friction.
Do not claim Java 25 as the real build baseline while the repo compiles with Java 21.
Rule: `.ai/primitives/rules/java-version.md`

### R2: Enterprise-Go layout in Java

Java modules follow: `domain/{entity,repository,usecase,service,rule}`, `application/{usecase/<aggregate>,mapper,dto}`, `infrastructure/{controller,consumer,repository,resilience,time}`, `cmd/`, `config/`.
Rule: `.ai/primitives/rules/architecture-clean.md`

### R3: ATDD first

Feature file before production code. Use Karate 1.5+ or Cucumber-JVM 7+ depending on context.
Rule: `.ai/primitives/rules/testing-atdd.md`

### R4: OTEL on every request

Every request produces trace + structured log + metric. `correlationId` must be in MDC and in the response header.
Rule: `.ai/primitives/rules/observability-otel.md`

### R5: Clean boundaries

`domain/` must not import `application/` or `infrastructure/`. Ports live in `domain/`; adapters live in `infrastructure/`.
Rule: `.ai/primitives/rules/clean-arch-boundaries.md`

### R6: Python with uv; JS/TypeScript with safe Bun

Python uses **uv** (`pyproject.toml`, `uv.lock`, `requirements.txt` exported by uv).
Node/JS/TypeScript uses **Bun** as package manager/runtime. Do not use npm/pnpm/yarn for installs.
`bunfig.toml` must keep `[install] ignoreScripts = true` to block lifecycle scripts.
Rule: `docs/40-bun-package-manager-security.md`

### R7: Screaming Architecture for services

Service source trees must reveal the business capability before framework/runtime/layer details. Java packages use capability-first prefixes such as `io.riskplatform.riskdecision.*`; TypeScript transactional PoCs keep Clean Architecture layers under `src/internal/transactional-risk/{domain,application,infrastructure}`.
Rule: `.ai/primitives/rules/screaming-architecture.md`


---

## 5. Extending the system

Before implementing any feature, find the matching skill:

```text
.ai/primitives/skills/
  add-rest-endpoint.md        add-sse-stream.md             add-websocket-channel.md
  add-webhook-subscription.md add-kafka-publisher.md        add-kafka-consumer.md
  add-fraud-rule.md           add-port-out.md               add-port-in.md
  add-domain-entity.md        add-value-object.md           add-otel-custom-span.md
  add-otel-custom-metric.md   add-resilience-pattern.md     add-idempotency-key.md
  add-outbox-event.md         add-helm-template.md          add-prometheus-rule.md
  add-feature-test-karate.md  add-feature-test-cucumber.md  add-jacoco-coverage-target.md
  add-mock-aws-service.md     add-architecture-decision.md  bootstrap-new-poc.md
  refactor-to-enterprise-layout.md benchmark-poc.md         debug-failing-test.md
  update-poc-readme.md        wire-engram-memory.md         update-architecture-doc.md
```

For multi-step tasks, use a workflow from `.ai/primitives/workflows/`.

---

## 6. Persistent memory: Engram

This project uses Engram MCP for cross-session memory.

- Project key: `real-time-risk-lab`
- Session start: `mem_context(project: "real-time-risk-lab")`
- Session end: `mem_session_summary(...)` is mandatory when Engram tools are available.
- Guide: `.ai/context/engram.md`

---

## 7. IDE adapters

| IDE/tool | Main file | Adapter |
|---|---|---|
| Claude Code | `CLAUDE.md` | `.ai/adapters/claude-code/` |
| Cursor | `.cursor/rules/*.mdc` | `.ai/adapters/cursor/` |
| Windsurf | `.windsurfrules` + `.windsurf/rules/` | `.ai/adapters/windsurf/` |
| GitHub Copilot | `.github/copilot-instructions.md` | `.ai/adapters/copilot/` |
| Codex CLI | `AGENTS.md` | `.ai/adapters/codex/` |
| opencode | `AGENTS.md` + `opencode.json` | `.ai/adapters/opencode/` |
| Kiro | `.kiro/steering/*.md` | `.ai/adapters/kiro/` |
| Antigravity | `GEMINI.md` | `.ai/adapters/antigravity/` |

Install all adapters:

```bash
for ide in claude-code cursor windsurf copilot codex opencode kiro; do
    bash ./.ai/adapters/$ide/install.sh
done
```

---

## 8. Exploration state

Current state: `.ai/context/exploration-state.md`

---

> Do not edit code before reading the applicable rule and workflow.
> If a primitive is missing, add it under `.ai/primitives/` before implementing.
> Verify the system with `./.ai/scripts/verify-primitives.sh`.
