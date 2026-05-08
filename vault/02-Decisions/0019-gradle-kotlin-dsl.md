---
adr: "0019"
title: Gradle 8 con Kotlin DSL y Version Catalog para pkg/* Modules
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/build, area/tooling]
---

# ADR-0019: Gradle 8 con Kotlin DSL y Version Catalog para pkg/* Modules

## Estado

Aceptado el 2026-05-07.

## Contexto

The `pkg/*` shared modules y `sdks/*` fueron established después de la PoCs proved la domain model. These modules son intended para ser long-lived, shared a través de PoCs y eventually used en production-like configurations. They require: multi-module build coordination, dependency version management que does no duplicate a través de modules, Java toolchain configuration, test runner setup (JUnit 5 + JaCoCo), y un clean extensibility model para adding new modules.

Gradle handles todos de these, pero a la cost de verbose parent POM inheritance y limited build logic reuse. Gradle con Kotlin DSL es la current industry direction para complex Java multi-module builds, y Gradle 8's configuration cache y build toolchain APIs provide meaningful performance advantages.

Gradle's composite build feature allows `build-logic/` para ser un separate Gradle build que produces convention plugins consumed por la main build, sin requiring plugin publication a un registry. Este es un cleaner model than Gradle parent POM inheritance.

## Decisión

Use Gradle 8.11.1 con Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) para todos `pkg/*` y `sdks/*` modules. Convention plugins son defined en `build-logic/` como un composite build. Version catalog es defined en `gradle/libs.versions.toml`. La Gradle wrapper (`gradlew`) ensures reproducible builds sin requiring un global Gradle installation.

Convention plugins: `riskplatform.java-conventions` (toolchain, encoding), `riskplatform.library-conventions` (extends java, adds test + JaCoCo), `riskplatform.app-conventions` (extends library, adds shadow JAR), `riskplatform.testing-conventions` (JUnit 5 + Testcontainers + AssertJ).

## Alternativas consideradas

### Opción A: Gradle 8 con Kotlin DSL + build-logic composite (elegida)
- **Ventajas**: Convention plugins en `build-logic/` son type-safe Kotlin — IDE autocompletion works; version catalog (`libs.versions.toml`) es un single source de truth para todos dependency versions; Gradle 8 configuration cache makes repeated `./gradlew build` near-instant después de la first run; composite build eliminates la need un publish convention plugins a un registry; Kotlin DSL es la Gradle team's recommended direction.
- **Desventajas**: Kotlin DSL has un steeper learning curve than Groovy DSL; composite build setup requires understanding de included builds vs subprojects; first build downloads ~130MB Gradle distribution.
- **Por qué se eligió**: La investment en `build-logic/` pays off como la number de `pkg/*` modules grows. Adding un new module requires one `include()` en `settings.gradle.kts` y un two-line `build.gradle.kts` con `id("riskplatform.library-conventions")` — todos toolchain, test, y coverage configuration es inherited.

### Opción B: Gradle 8 con Groovy DSL
- **Ventajas**: More `build.gradle` examples online; familiar un developers desde pre-Kotlin era; slightly menos verbose para simple cases.
- **Desventajas**: No IDE type-checking; Gradle team recommends migration un Kotlin DSL; Groovy DSL support es en maintenance mode; string-based plugin IDs sin type safety.
- **Por qué no**: La Kotlin DSL investment es worth making now. Groovy DSL advantage (familiarity) does no apply here since la project es new.

### Opción C: Gradle para pkg/* con parent POM convention
- **Ventajas**: Consistent con PoC build tool (ADR-0018); Gradle parent POM provides convention reuse; well-understood lifecycle.
- **Desventajas**: Parent POM inheritance es brittle — modules que override parent configuration create implicit coupling; no equivalent de Gradle's composite build para plugin logic; Gradle version catalogs (via BOM) son menos ergonomic than `libs.versions.toml`; Gradle's incremental compilation es weaker than Gradle's.
- **Por qué no**: Gradle's composite build model es demonstrably cleaner para convention reuse a scale. La deliberate split (Gradle PoCs, Gradle shared libs) establishes un migration direction.

### Opción D: Bazel
- **Ventajas**: True incremental builds; hermetic; scales un very large monorepos; remote build cache.
- **Desventajas**: Extremely steep learning curve; requires `BUILD` files para every target; Java ecosystem tooling (IntelliJ, Spring tooling) has limited Bazel integration; no standard en la target Java ecosystem; would dominate setup time para minimal exploration benefit.
- **Por qué no**: Bazel es appropriate a Google/Meta scale. For un preparation monorepo, la engineering cost de Bazel adoption vastly exceeds la benefit.

## Consecuencias

### Positivo
- Adding un new `pkg/*` module es un 2-line `build.gradle.kts` (`id("riskplatform.library-conventions")`) + 1 `include()` en `settings.gradle.kts`.
- All modules share la same Java 21 toolchain configuration (`--release 21`) — no drift entre modules.
- `libs.versions.toml` es la single place un bump un dependency version; todos modules pick it up en next build.
- Configuration cache makes `./gradlew :pkg:resilience:test` near-instant en repeated runs.

### Negativo
- `build-logic/` es un additional Gradle build que must be understood antes de modifying convention plugins.
- Kotlin DSL error messages son sometimes cryptic when un type mismatch occurs en plugin configuration.
- IntelliJ occasionally requires `Sync Gradle` después de modifying `build-logic/` — un extra step compared un plain-module builds.

### Mitigaciones
- `BUILDING.md` documents la two-build structure y common commands.
- Convention plugins son kept minimal: each does one thing (toolchain, testing, app packaging).

## Validación

- `./gradlew build` builds todos `pkg/*` y `sdks/*` modules desde scratch.
- `./gradlew :pkg:resilience:test` passes ArchUnit y unit tests.
- `./gradlew :pkg:resilience:dependencies` shows la correct Java 21 toolchain.
- Second run de `./gradlew build` reports `BUILD SUCCESSFUL` con configuration cache hit.

## Relacionado

- [[0018-maven-before-gradle]]
- [[0026-convention-plugins]]
- [[0020-pkg-shared-modules]]

## Referencias

- Gradle Convention Plugins: https://docs.gradle.org/current/samples/sample_convention_plugins.html
- Gradle Version Catalog: https://docs.gradle.org/current/userguide/version_catalogs.html
- BUILDING.md: `BUILDING.md`
