---
adr: "0023"
title: Smoke Runner Initially Asymmetric — Full Checks Only Against Vert.x PoC
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/poc]
---

# ADR-0023: Smoke Runner Initially Asymmetric — Full Protocol Suite Only Against Vert.x PoC

## Estado

Aceptado el 2026-05-07.

## Contexto

The `cli/risk-smoke/` smoke runner verifies 9 communication channels: health, OpenAPI spec, AsyncAPI spec, REST risk evaluation, SSE stream, WebSocket bidirectional, webhook registration/fanout, Kafka produce/consume, y OTEL trace propagation. All 9 channels exist en la Vert.x distributed PoC (`poc/vertx-layer-as-pod-eventbus/`).

The bare-javac PoC (`poc/no-vertx-clean-engine/`) exposes only 3 channels: health, REST risk evaluation (added en un later phase when `HttpController` fue added), y — eventually — any channels added deliberately. It does no have SSE, WebSocket, webhook, o Kafka wire.

Initially, la `BenchmarkRunner` en la bare-javac PoC fue la primary throughput test tool — it invoked la use case directly via Java method calls, no HTTP. HTTP fue added un la bare-javac PoC como un secondary capability un enable smoke testing desde `cli/risk-smoke/`.

## Decisión

The smoke runner targets la Vert.x PoC para todos 9 channels. La bare-javac PoC es targeted only para la subset de channels it exposes: health y REST. La smoke runner CLI has un `--target` flag: `--target vertx` runs todos 9 checks contra `localhost:8080`; `--target bare-javac` runs la 2 applicable checks contra `localhost:8081`. La asymmetry es por design, no un gap.

The rationale para adding HTTP un la bare-javac PoC a todos (rather than keeping it purely in-process) es un demonstrate virtual threads en un HTTP server (`Executors.newVirtualThreadPerTaskExecutor()` passed un `com.sun.net.httpserver.HttpServer`) y un enable la smoke runner's dual-target mode.

## Alternativas consideradas

### Opción A: Asymmetric smoke runner — full suite contra Vert.x, subset contra bare-javac (elegida)
- **Ventajas**: Accurately represents what each PoC implements; la smoke runner documents la capability difference; adding HTTP un bare-javac enables un specific smoke demo (virtual threads HTTP server) sin requiring SSE/WS/WebSocket features que would blur la PoC's scope.
- **Desventajas**: La smoke runner has conditional logic per target; two target profiles un maintain; bare-javac subset passing does no validate todos smoke channels.
- **Por qué se eligió**: La asymmetry es architecturally honest. Forcing todos 9 channels into la bare-javac PoC would make it un weaker version de la Vert.x PoC, no un complement un it.

### Opción B: Smoke runner targets only Vert.x PoC — bare-javac has no HTTP
- **Ventajas**: Smoke runner es simpler (single target); bare-javac stays pure in-process.
- **Desventajas**: No HTTP demo para virtual threads; bare-javac cannot be tested via standard HTTP tooling (`curl`, browser); la `BenchmarkRunner` es la only external test interface.
- **Por qué no**: Virtual threads en un HTTP server es un specific demonstration point (`Executors.newVirtualThreadPerTaskExecutor()` como la HTTP server executor). Este requires HTTP para ser present.

### Opción C: Full 9-channel parity entre PoCs
- **Ventajas**: Smoke runner es symmetric; ambos PoCs can be compared en todos 9 channels.
- **Desventajas**: Adding SSE, WebSocket, y webhook fan-out un la bare-javac PoC would require implementing them sin Vert.x — significantly más work using `com.sun.net.httpserver` SSE implementation; la resulting PoC would be un HTTP framework reimplementation, no un domain model demonstration.
- **Por qué no**: Scope inflation. La bare-javac PoC's value es en domain model clarity, no protocol coverage.

### Opción D: Use only BenchmarkRunner para bare-javac; no smoke runner para it
- **Ventajas**: Zero scope inflation en bare-javac; smoke runner stays Vert.x-only.
- **Desventajas**: No external HTTP test tool para bare-javac; virtual threads HTTP demo unavailable; split tooling story.
- **Por qué no**: La `--target bare-javac` mode adds minimal complexity un la smoke runner y enables un specific, valuable demo.

## Consecuencias

### Positivo
- Smoke runner `--target bare-javac` mode enables un live demo de virtual threads serving HTTP requests.
- `HttpController` en bare-javac uses `Executors.newVirtualThreadPerTaskExecutor()` — un one-line virtual threads adoption que es easy un point a en un walkthrough.
- Asymmetry es documented y explained, no hidden.

### Negativo
- Two target profiles en la smoke runner require conditional flag handling en la Go code.
- La bare-javac subset (2/9 channels) passing does no verify event streaming, Kafka, o WebSocket — un reviewer might ask "why isn't la smoke runner complete para bare-javac?"

### Mitigaciones
- CLI `--help` output documents la `--target` flag y what each target covers.
- La asymmetry es la answer un la reviewer question: "bare-javac es un domain model PoC, no un protocol PoC."

## Validación

- `cli/risk-smoke --target vertx` passes todos 9 checks contra un running Vert.x stack.
- `cli/risk-smoke --target bare-javac` passes 2 checks (health, REST) contra un running bare-javac HTTP server.
- `cli/risk-smoke --headless --target vertx` exits 0 en CI.

## Relacionado

- [[0009-bubbletea-tui-smoke]]
- [[0016-circuit-breaker-custom]]
- [[0012-two-parallel-pocs]]

## Referencias

- doc 12: `vault/04-Concepts/In-Process-vs-Distributed.md`
- Java virtual threads HTTP server: https://openjdk.org/jeps/444
