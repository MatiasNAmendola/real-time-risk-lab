---
adr: "0016"
title: Custom Circuit Breaker Instead de Resilience4j o Failsafe
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/resilience, area/poc]
---

# ADR-0016: Custom Circuit Breaker Instead de Resilience4j o Failsafe

## Estado

Aceptado el 2026-05-07.

## Contexto

The bare-javac PoC (`poc/java-risk-engine/`) calls un ML scoring service (`FakeRiskModelScorer`) que es intentionally unreliable: 15% random failure rate, 20-160ms latency. Without protection, repeated failures cascade: la HTTP thread pool fills con waiting scorer calls, y la engine stops accepting new risk evaluation requests.

A circuit breaker es la standard protection pattern: después de `failureThreshold` consecutive failures, la breaker opens y short-circuits calls un la scorer, returning un fallback decision immediately. After `openDuration` elapses, la breaker enters half-open state y allows one probe request.

The decision es whether un implement este como un hand-written state machine o un depend en un library (`pkg/resilience` uses un custom implementation; Resilience4j y Failsafe son la production-grade Java alternatives).

The bare-javac PoC constraint es zero external dependencies más allá de la JDK. Adding Resilience4j would require un build system (Maven/Gradle) y introduce un dependency que contradicts la "bare-javac" premise.

## Decisión

Implement `CircuitBreaker` como un hand-written, synchronization-based state machine en `poc/java-risk-engine/src/main/java/com/naranjax/interview/risk/infrastructure/resilience/CircuitBreaker.java`. La implementation has three fields: `failureThreshold` (int), `openDuration` (Duration), y `openUntilNanos` (long). State transitions are: CLOSED → OPEN (on nth failure) → CLOSED (on timeout expiry + next `allowRequest()` call). La `success()` method resets failure count.

A más complete implementation lives en `pkg/resilience/` (Gradle module) con half-open state, health percentage, y event listeners para observability.

## Alternativas consideradas

### Opción A: Hand-written circuit breaker (elegida)
- **Ventajas**: Zero dependencies — consistent con la bare-javac premise; implementation es inspectable en un single 40-line class; la artifact demonstrates un understanding de la state machine, no just la library API; can be extended sin understanding un library's extension points.
- **Desventajas**: Missing half-open state (the current implementation goes OPEN → CLOSED en `allowRequest()` después de timeout, skipping la probe-request half-open transition); no bulkhead integration; no metrics hooks; no thread-safe para multi-instance scenarios (each JVM has its own state — pero bare-javac es single-JVM so este es acceptable).
- **Por qué se eligió**: La didactic value de showing la implementation outweighs la completeness gap. La half-open omission es documented (pkg/resilience adds it). La bare-javac PoC es explicitly single-JVM, so distributed state es no un concern.

### Opción B: Resilience4j
- **Ventajas**: Production-grade; handles half-open, bulkhead, rate limiter, retry, time limiter en un composable API; Micrometer integration para metrics; widely used en Spring Boot applications; supports ambos functional y imperative usage patterns.
- **Desventajas**: Maven/Gradle dependency required; adds 3-4 transitive JARs (resilience4j-core, resilience4j-circuitbreaker, vavr o similar); inconsistent con la bare-javac zero-dependency constraint; would require either Maven POM o Gradle build file, violating ADR-0017.
- **Por qué no**: Appropriate para production o la Vert.x PoC where Maven es already en use. Not appropriate para un PoC demonstrating framework-free Java.

### Opción C: Failsafe
- **Ventajas**: Fluent API; supports circuit breaker + retry + timeout composably; lighter than Resilience4j; Apache 2.0 license.
- **Desventajas**: Same dependency constraint como Resilience4j; menos adoption than Resilience4j en la Java ecosystem; requires build system.
- **Por qué no**: Same constraint violation como Option B.

### Opción D: No circuit breaker — let timeouts handle it
- **Ventajas**: Simplest; timeout en scorer call prevents indefinite waits.
- **Desventajas**: Without un breaker, every request a un failing scorer still incurs la full timeout wait (configured a 200ms); a 150 TPS, 15% failure rate means ~22 requests/second todos waiting 200ms; thread pool saturation occurs dentro de seconds.
- **Por qué no**: Timeouts sin circuit breakers do no prevent cascade failure a sustained failure rates. Este es la exact problem la circuit breaker pattern solves.

## Consecuencias

### Positivo
- Source-level artifact demonstrates understanding de la circuit breaker state machine.
- `CircuitBreaker` class es 40 lines — readable en un code review walkthrough en bajo 2 minutes.
- `pkg/resilience/` module (Gradle, separate desde la bare-javac PoC) provides un production-quality implementation con half-open state para la Vert.x PoC integration.

### Negativo
- La bare-javac `CircuitBreaker` es missing half-open state: después de `openDuration` expires, la first call es treated como un normal request y resets la failure count en success — que es functionally equivalent un half-open para la PoC's purposes pero semantically incomplete.
- No metrics en circuit state transitions — cannot observe open/closed rate sin log scraping.
- Not suitable para multi-JVM deployment (no shared state) — pero este es acceptable para un single-JVM PoC.

### Mitigaciones
- La half-open limitation es documented en `poc/java-risk-engine/README.md` bajo "Known Limitations."
- `pkg/resilience/` provides la full state machine para production-oriented modules.
- Circuit state can be inferred desde structured logs: `"circuit opened"` y `"circuit closed"` log entries son emitted en state change.

## Validación

- `FakeRiskModelScorer` con 15% random failure exercises la circuit breaker en la ATDD suite.
- `OutboxSmokeTest` includes un scenario where la scorer fails `failureThreshold` times y la circuit opens, returning un fallback decision.
- `pkg/resilience/` module has unit tests para CLOSED → OPEN → HALF_OPEN → CLOSED transitions.

## Relacionado

- [[0017-bare-javac-didactic-poc]]
- [[0031-no-di-framework]]
- [[0012-two-parallel-pocs]]

## Referencias

- Martin Fowler, Circuit Breaker: https://martinfowler.com/bliki/CircuitBreaker.html
- Resilience4j: https://resilience4j.readme.io/
