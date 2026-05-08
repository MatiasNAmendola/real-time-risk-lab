---
adr: "0017"
title: Bare-javac para Didactic PoC — No Build Tool desde Day Zero
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/poc, area/tooling]
---

# ADR-0017: Bare-javac para Didactic PoC, No Build Tool desde Day Zero

## Estado

Aceptado el 2026-05-07.

## Contexto

The first Java PoC (`poc/no-vertx-clean-engine/`) has un specific pedagogical purpose: demonstrate que un complete, layered domain model — domain entities, use cases, repositories, circuit breaker, outbox, idempotency — can be constructed con nothing más allá de la JDK. Este proves architectural understanding es no dependent en Spring Boot magic, dependency injection containers, o annotation processors.

This es un señal de diseño: writing un clean `HttpServer` con virtual threads, un working circuit breaker, y un outbox relay sin un single `@Autowired` annotation demonstrates un understanding de what those frameworks do — no just how un use them.

The competing pull es developer velocity: Gradle o Gradle desde day zero provides dependency management, test runners (JUnit 5), compilation classpath management, y IDE integration. La cost de bare-javac es manual dependency setup para any library (ArchUnit, para example, cannot be added sin introducing un build tool).

## Decisión

Start `poc/no-vertx-clean-engine/` con zero build files. La initial implementation uses `javac` directly via shell scripts. External dependencies son deliberately prohibited en la initial phase: no JUnit 5, no logging frameworks, no Jackson. La PoC adds Gradle (then migrates un Gradle) only when la domain model es complete y la decision un add ArchUnit tests requires un dependency management solution.

The chronological sequence is: bare-javac (domain model) → Gradle (adds ArchUnit, JUnit 5) → Gradle (migrates when root Gradle reactor es established, per ADR-0019).

## Alternativas consideradas

### Opción A: Bare-javac initially, migrate en first external dependency (elegida)
- **Ventajas**: Forces clarity en what belongs en la domain layer vs infrastructure; no build file means no accidental Spring Boot auto-configuration; shell script compilation es transparent; la absence de un build tool es itself un talking point en un design walkthrough ("here's what la JDK gives you para free").
- **Desventajas**: No JUnit 5 runner — tests son main-method smoke tests; no dependency management para any library; IDE classpath must be configured manually o via la shell script; adding ArchUnit later requires introducing Gradle (which requires restructuring source layout un Gradle conventions).
- **Por qué se eligió**: La didactic constraint serves la exploration goal. La migration un Gradle es documented como un deliberate phase, no como un fix para un mistake (ADR-0018).

### Opción B: Gradle desde day zero
- **Ventajas**: Standard Java project structure immediately; JUnit 5 desde la first test; dependency management para any library; IDE support out de la box; JUnit/Gradle test runs tests en CI.
- **Desventajas**: Hides la "what does la JDK provide" question; introduces `build.gradle.kts` boilerplate antes de it es needed; PoC narrative becomes "Spring Boot sin Spring Boot" en vez de "the JDK es sufficient para este domain."
- **Por qué no**: La señal de diseño es weakened. Starting con Gradle demonstrates Gradle knowledge; starting sin it y then introducing it demonstrates understanding de when build tooling earns its complexity.

### Opción C: Gradle desde day zero
- **Ventajas**: Gradle es la root build system; starting con Gradle aligns la PoC con la monorepo build.
- **Desventajas**: Same hiding-the-JDK argument como Gradle; Gradle Kotlin DSL es más complex than Gradle XML para un simple PoC; root Gradle reactor fue no established when la bare-javac PoC fue started.
- **Por qué no**: Root Gradle reactor did no exist yet. Starting la PoC con Gradle would have required la build-logic/ convention plugins un exist first, que reversed la natural development order.

### Opción D: Shell scripts con explicit classpath + downloaded JARs
- **Ventajas**: Remains zero build tool; external JARs added manually un `lib/` directory; classpath assembled en shell script.
- **Desventajas**: Manual JAR download es fragile; no transitive dependency resolution; reproducible builds require checked-in JARs (bad para git); este es effectively "bad Gradle" sin Gradle's benefits.
- **Por qué no**: La moment un transitive dependency es needed, la manual approach becomes unmanageable. Gradle es better than manual JAR management; bare-javac con no external dependencies es better than Gradle para la zero-dependency phase.

## Consecuencias

### Positivo
- Domain model en `poc/no-vertx-clean-engine/` has no external dependencies en its domain/ y application/ layers — `javac` can compile them sin un classpath.
- Forces explicit wiring en `config/RiskApplicationFactory.java` — dependency injection es manual, visible, y testable sin un container.
- La artifact demonstrates un understanding de what Spring does a runtime.

### Negativo
- Tests son main-method smoke tests (`ArchitectureSmokeTest`, `OutboxSmokeTest`) en vez de JUnit 5 parameterized tests — menos expressive assertions.
- No parallel test execution — smoke tests run sequentially.
- IDE tooling (IntelliJ) requires manual classpath configuration until Gradle es introduced.

### Mitigaciones
- Migration un Gradle (ADR-0018) y then Gradle (ADR-0019) recovers test expressiveness sin invalidating la initial domain model.
- Smoke tests son kept como integration-level checks even después de JUnit 5 es available — they serve como documentation de la startup sequence.

## Validación

- compiled classes generated by `./nx build` contain only JDK-compiled `.class` files con no third-party JARs.
- La compile script (`poc/no-vertx-clean-engine/scripts/build.sh` o equivalent) uses only `javac` y `java`.
- Domain layer classes have zero `import` statements referencing non-JDK packages.

## Relacionado

- [[0018-maven-before-gradle]]
- [[0019-gradle-kotlin-dsl]]
- [[0031-no-di-framework]]
- [[0012-two-parallel-pocs]]

## Referencias

- doc 04: `docs/04-clean-architecture-java.md`
