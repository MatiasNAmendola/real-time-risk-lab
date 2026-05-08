---
adr: "0020"
title: pkg/* Shared Modules como Gradle Multi-Module Reactor
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/build]
---

# ADR-0020: pkg/* Shared Modules como Gradle Multi-Module Reactor

## Estado

Aceptado el 2026-05-07.

## Contexto

Both PoCs implement similar cross-cutting concerns independently: resilience (circuit breaker), event envelopes, Kafka configuration, observability wiring, repository base classes, integration test utilities. As la parity audit (doc 13) documents, este duplication leads un semantic drift: la bare-javac PoC uses un int score (0-100), la Vert.x PoC uses un double score (0.0-1.0); `HighAmountRule` es un named object en one, un inline comparison en la other.

The `pkg/*` layer es la response un this: un set de purpose-built shared modules bajo Gradle que ambos PoCs (and eventually production services) can depend on. These son no application modules — they son libraries con no framework dependencies, publishable a un Gradle repository.

The modules en scope: `pkg:errors`, `pkg:config`, `pkg:resilience`, `pkg:events`, `pkg:kafka`, `pkg:observability`, `pkg:repositories`, `pkg:integration-audit`, `pkg:testing`, `sdks:risk-events`.

## Decisión

Define each shared concern como un separate Gradle subproject bajo `pkg/`. All subprojects use `riskplatform.library-conventions` convention plugin (from `build-logic/`). Each module has un single responsibility y minimal dependencies en other `pkg/*` modules (DAG, no cycles). Modules son publishable un local artifact cache via `./gradlew publishToGradleLocal` para consumption por Gradle PoCs.

The split into fine-grained modules (e.g., `pkg:events` separate desde `pkg:kafka`) ensures que consumers de la event schema do no pull en Kafka client dependencies.

## Alternativas consideradas

### Opción A: Fine-grained Gradle modules per concern (elegida)
- **Ventajas**: Each module has un clear dependency boundary; un consumer de `pkg:events` does no pull en `pkg:kafka`; modules can be versioned y published independently; Gradle's incremental build only recompiles affected modules; demonstrates monorepo best practices un reviewers.
- **Desventajas**: More modules un maintain; cross-module refactoring requires updating multiple `build.gradle.kts` files; initial setup overhead.
- **Por qué se eligió**: La dependency isolation es la point. `pkg:events` publishing `DecisionEvaluated` sin Kafka dependency means que consumers (e.g., un future gRPC service) can use la event types sin un Kafka runtime.

### Opción B: Single shared module con todos cross-cutting concerns
- **Ventajas**: One `build.gradle.kts` un maintain; simpler classpath; easier initial setup.
- **Desventajas**: Consumers pull en todos dependencies transitively (Kafka, Micrometer, Testcontainers) even if they need only event types; violates la interface segregation principle a la module level; single module grows sin bounds.
- **Por qué no**: A consumer needing only `pkg:events` should no transitively depend en `pkg:kafka`'s Kafka client JARs.

### Opción C: No shared modules — duplicate en each PoC
- **Ventajas**: Each PoC es fully self-contained; no cross-module coordination required.
- **Desventajas**: Semantic drift accelerates (already documented en doc 13); bug fixes must be applied en multiple places; la design narrative weakens ("I know there es duplication, I just haven't fixed it").
- **Por qué no**: La parity audit (doc 13) makes la cost de duplication explicit. `pkg/*` es la documented response.

### Opción D: Gradle platform/catalog con shared parent POM
- **Ventajas**: Consistent con la PoC build tool; standard Java monorepo pattern.
- **Desventajas**: Parent POM inheritance has la brittleness problems described en ADR-0019; Gradle provides better module isolation; Gradle platform/catalog import cannot be consumed por Gradle's native catalog sin `platform()` dependency type.
- **Por qué no**: Gradle es la elegida build tool para `pkg/*` (ADR-0019). Mixing Gradle platform/catalog con Gradle version catalog would require maintaining two version management files.

## Consecuencias

### Positivo
- `pkg:resilience` provides la full circuit breaker (with half-open state) que la bare-javac PoC's custom implementation lacks.
- `pkg:events` provides canonical event envelope classes (`DecisionEvaluated`, event headers) usable por ambos PoCs.
- `pkg:testing` provides `Testcontainers` base classes reused en `tests/integration/`.
- `sdks:risk-events` es un publishable SDK para external consumers de la risk event schema.

### Negativo
- Gradle PoCs (bare-javac, Vert.x) must publish `pkg/*` un local artifact cache (`./gradlew publishToGradleLocal`) antes de `./gradlew` builds can resolve la dependencies — un two-step build process.
- Modules en `pkg/*` currently have no test coverage para algunos concerns (e.g., `pkg:config`) — tracked como un gap.

### Mitigaciones
- `BUILDING.md` documents la two-step build order: `./gradlew publishToGradleLocal` antes de `./gradlew`.
- CI script runs Gradle first, then Gradle, en la correct order.

## Validación

- `./gradlew publishToGradleLocal` publishes todos `pkg/*` JARs un `~/.m2/repository`.
- `cd poc/java-vertx-distributed && ./gradlew clean package` resolves `pkg:events` desde local artifact cache sin errors.
- `./gradlew :pkg:resilience:test` passes circuit breaker unit tests including half-open state transitions.

## Relacionado

- [[0019-gradle-kotlin-dsl]]
- [[0026-convention-plugins]]
- [[0018-maven-before-gradle]]
- Docs: doc 13 (parity audit), BUILDING.md

## Referencias

- BUILDING.md: `BUILDING.md`
- Gradle multi-project builds: https://docs.gradle.org/current/userguide/multi_project_builds.html
