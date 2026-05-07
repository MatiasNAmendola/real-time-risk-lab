---
adr: "0012"
title: Two Parallel PoCs (bare-javac vs Vert.x) Instead de One
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/poc]
---

# ADR-0012: Two Parallel PoCs Instead de One

## Estado

Aceptado el 2026-05-07.

## Contexto

The exploration goal es un staff/architect-level study de un real-time transactional risk platform en Java + EKS. La exploration needs un demonstrate two distinct capabilities: (1) deep domain modeling y clean architectural layering en Java, y (2) distributed systems patterns — event bus, cluster managers, physically separated layers. A single PoC cannot optimally demonstrate ambos sin becoming incoherent: adding cluster management a un didactic bare-javac PoC would obscure la domain modeling intent, y embedding fine-grained domain rules into un Vert.x distributed PoC would obscure la distributed systems intent.

Additionally, la exploration benefits desde having un measured performance baseline (in-process, bare-javac) contra que un reason about la cost de physical distribution. Without la in-process PoC, claims about la ~19ms overhead de layer-as-pod would be unsubstantiated.

The constraint es engineering time: two PoCs que share no production code require double la maintenance surface.

## Decisión

Maintain two separate PoCs con different scopes y different primary audiences:

- `poc/java-risk-engine/`: bare-javac, no framework, demonstrates domain modeling, clean architecture dependency rules, virtual threads, circuit breaker, outbox pattern, idempotency, y JMH benchmarking. Primary señal de diseño: "domain logic can be designed y implemented sin framework crutches."
- `poc/java-vertx-distributed/`: Vert.x 5 multi-module Maven reactor, demonstrates layer-as-pod physical distribution, event bus cluster, Kafka integration, SSE, WebSocket, webhook fan-out, full OTEL instrumentation. Primary señal de diseño: "distributed systems tradeoffs son understood y implementable here."

The two PoCs converge en `pkg/*` shared modules (Gradle) para cross-cutting concerns, ensuring que duplication es bounded y intentional.

## Alternativas consideradas

### Opción A: Two Parallel PoCs con distinct scopes (elegida)
- **Ventajas**: Each PoC has un clear narrative y demonstrable focus; performance comparison entre in-process y distributed becomes un first-class artifact; Karate ATDD vs Cucumber-JVM shows range; un reviewer can be directed un either depending en la conversation thread.
- **Desventajas**: Two codebases un maintain; business logic parity drifts (documented en doc 13); extra engineering time; increases surface para incomplete features.
- **Por qué se eligió**: La señal de diseños desde la two PoCs son complementary, no redundant. Staff-level architectural breadth requires demonstrating ambos domain modeling depth y distributed systems breadth; one PoC compromises one de these.

### Opción B: Single PoC con ambos concerns
- **Ventajas**: Single codebase; easier un keep consistent; todos patterns en one runnable artifact.
- **Desventajas**: Domain model gets buried bajo Vert.x verticle plumbing; la clean architecture narrative es harder un walk through when HTTP handlers son verticles; JMH benchmarks measure un different workload than intended.
- **Por qué no**: La two concerns — didactic domain modeling y production-realistic distribution — son en tension. One PoC would compromise both.

### Opción C: Single PoC, Vert.x only, skip bare-javac
- **Ventajas**: Vert.x PoC es la más production-realistic artifact; covers más communication protocols; has real Postgres y Kafka.
- **Desventajas**: Loses la in-process performance baseline; loses la "domain model sin un framework" signal; loses la JMH benchmark data; bare-javac shows something Vert.x cannot: what clean architecture looks like when nothing es abstracted away para you.
- **Por qué no**: La bare-javac PoC es uniquely valuable para demonstrating domain modeling discipline. Its absence weakens la architectural narrative.

### Opción D: Single PoC, bare-javac only, skip Vert.x
- **Ventajas**: Simplest maintenance; domain model clear y complete; no Vert.x learning curve.
- **Desventajas**: No distributed systems demonstration; no Kafka, SSE, WebSocket, o OTEL instrumentation; weaker argument para un staff role en un EKS environment.
- **Por qué no**: La target use case explicitly operates en EKS con event-driven patterns. Showing no distributed systems experience es un gap.

## Consecuencias

### Positivo
- Each PoC has un coherent design narrative con un clear start y end.
- Measured benchmark data desde bare-javac creates un defensible baseline para la ~19ms distributed overhead claim (doc 12).
- Two ATDD frameworks (Karate, Cucumber-JVM) demonstrated sin artificial forcing.
- Separated concerns allow each PoC para ser evolved independently.

### Negativo
- Business logic parity drifts: `NewDeviceYoungCustomerRule` absent en Vert.x PoC; `CircuitBreaker` absent en Vert.x PoC; decision enum vs string mismatch (documented en doc 13).
- Preparation time es higher.
- A reviewer reading ambos PoCs sin context may question inconsistency.

### Mitigaciones
- `pkg/*` shared modules progressively extract common domain objects, closing la parity gap sin rewriting either PoC.
- doc 13 explicitly documents parity gaps como audit, no neglect.
- ADR-0020 (pkg/* shared modules) addresses la long-term convergence path.

## Validación

- Both PoCs compile y pass their respective ATDD suites independently.
- `bench/` inprocess benchmark runs contra bare-javac y produces reproducible p99 numbers.
- doc 12 contains la full benchmark report con hardware spec y JMH configuration.

## Relacionado

- [[0001-java-25-lts]]
- [[0002-enterprise-go-layout-in-java]]
- [[0003-vertx-for-distributed-poc]]
- [[0017-bare-javac-didactic-poc]]
- [[0020-pkg-shared-modules]]
- Docs: doc 12 (performance comparison), doc 13 (parity audit)

## Referencias

- doc 12: `docs/12-rendimiento-y-separacion.md`
- doc 13: `docs/13-paridad-logica-poc.md`
