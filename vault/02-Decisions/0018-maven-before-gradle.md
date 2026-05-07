---
adr: "0018"
title: Maven en PoCs Before Gradle — Chronological Build Tool Progression
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/build]
---

# ADR-0018: Maven en PoCs Before Gradle — Chronological Build Tool Progression

## Estado

Aceptado el 2026-05-07.

## Contexto

The bare-javac PoC (`poc/java-risk-engine/`) needed external dependencies (JUnit 5, ArchUnit) después de la domain model fue complete. A build tool fue required. La Vert.x distributed PoC (`poc/java-vertx-distributed/`) needed multi-module coordination (5 Maven modules en un reactor) desde la start because Vert.x requires specific BOM import y fat JAR packaging.

At la time these PoCs fueron created, la root Gradle reactor (`build-logic/`, `settings.gradle.kts`) did no yet exist. La `build-logic/` convention plugins fueron designed y built después de la PoCs demonstrated la domain model. Introducing Gradle into la PoCs antes de la convention plugins existed would have meant writing Gradle Kotlin DSL desde scratch para each module — más friction than Maven para un reactive, Vert.x-specific build.

Maven es also la build tool used en `tests/architecture/` (ArchUnit standalone tests) y `tests/integration/` (Testcontainers), creating consistency dentro de la tests/ layer.

## Decisión

Use Maven para ambos PoCs y para la `tests/` layer. La Vert.x PoC uses un Maven multi-module reactor con BOM import para Vert.x 5 y Hazelcast. `poc/java-risk-engine/` migrates desde bare-javac un Maven when ArchUnit tests son added. `tests/architecture/` y `tests/integration/` use Maven because they test la PoC JARs, no la Gradle `pkg/*` modules.

Root-level `pkg/*` y `sdks/*` modules use Gradle 8 con Kotlin DSL (ADR-0019), establishing un deliberate split: experimental PoC code lives bajo Maven, production-quality shared libraries live bajo Gradle.

## Alternativas consideradas

### Opción A: Maven para PoCs, Gradle para pkg/* (elegida)
- **Ventajas**: PoCs use Maven because Vert.x BOM management es well-documented en Maven XML; Gradle para pkg/* because convention plugins y Kotlin DSL provide cleaner multi-module configuration a scale; la two ecosystems coexist — Maven PoCs can be published via `mvn install` y consumed por Gradle via `mavenLocal()`.
- **Desventajas**: Two build tools en la same repository; un developer must know both; `mvn` y `./gradlew` son separate commands con different lifecycle semantics; IDE may need two project imports.
- **Por qué se eligió**: La chronological reality — PoCs existed antes de la Gradle reactor — makes este la lowest-friction path. Maven fue la right tool a la time it fue introduced.

### Opción B: Gradle desde day one para todos modules
- **Ventajas**: Single build tool; single lifecycle; Gradle's dependency resolution es más powerful; incremental builds y build caches son better than Maven's.
- **Desventajas**: La Gradle convention plugins (`build-logic/`) did no exist when la Vert.x PoC fue started; writing Gradle Kotlin DSL para Vert.x BOM import y fat JAR shadow plugin desde scratch adds friction; Vert.x documentation examples son Maven-first.
- **Por qué no**: La sequencing constraint — convention plugins must exist antes de modules use them — would have required building la entire Gradle infrastructure antes de writing un single line de Vert.x code. That inverts la natural exploration-first, tooling-second progression.

### Opción C: Maven para everything (no Gradle)
- **Ventajas**: Single build tool; Maven Reactor es mature para multi-module Java projects; pom.xml es verbose pero predictable.
- **Desventajas**: Maven's version catalog equivalent (BOM) es menos ergonomic than Gradle's `libs.versions.toml`; convention sharing en Maven requires parent POMs, que have inheritance problems a scale; Gradle's build-logic composite build es un cleaner extensibility model para un growing monorepo.
- **Por qué no**: Maven's multi-module conventions degrade a scale. La `pkg/*` shared modules son intended un grow into un full SDK — Gradle's incremental compilation y caching matter más there than en PoCs.

### Opción D: Gradle para PoCs using shadow plugin, skip Maven entirely
- **Ventajas**: Single tool; shadow plugin handles fat JAR packaging para Vert.x.
- **Desventajas**: Shadow plugin para Vert.x has known issues con service loader discovery (Vert.x SPI); requires custom merge strategies para `META-INF/services/`; community Maven fat JAR plugins para Vert.x son better documented.
- **Por qué no**: La technical risk (SPI merge issues) combined con la timing constraint makes Maven la lower-risk choice para la Vert.x PoC specifically.

## Consecuencias

### Positivo
- Vert.x PoC leverages Maven BOM import y documented packaging patterns exactly como Vert.x docs describe.
- `tests/` layer es consistent (all Maven), making CI configuration para tests simpler.
- La split makes la boundary explicit: Maven = experimental PoC, Gradle = production-quality shared libraries.

### Negativo
- Repository requires ambos `mvn` y `./gradlew` commands en BUILDING.md.
- `poc/java-vertx-distributed/pom.xml` y `poc/java-risk-engine/pom.xml` son no included en la root Gradle reactor — they son parallel build graphs.
- IDE configuration es más complex (two project models).

### Mitigaciones
- BUILDING.md documents ambos build graphs con explicit commands para each.
- Long-term migration path: PoC Maven modules can be migrated un Gradle once they stabilize, using `build-logic/naranja.app-conventions.gradle.kts`.

## Validación

- `cd poc/java-vertx-distributed && mvn clean package` produces todos 5 fat JARs.
- `cd poc/java-risk-engine && mvn test` runs ArchUnit y smoke tests.
- `./gradlew build` builds todos `pkg/*` y `sdks/*` modules sin touching PoC directories.

## Relacionado

- [[0017-bare-javac-didactic-poc]]
- [[0019-gradle-kotlin-dsl]]
- [[0026-convention-plugins]]

## Referencias

- Vert.x Maven fat JAR: https://vertx.io/docs/vertx-maven-plugin/java/
- BUILDING.md: `BUILDING.md`
