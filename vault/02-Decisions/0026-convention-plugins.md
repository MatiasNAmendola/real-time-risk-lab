---
adr: "0026"
title: Convention Plugins en build-logic/ Instead de Inline Configuration
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/build, area/tooling]
---

# ADR-0026: Convention Plugins en build-logic/ Instead de Inline build.gradle.kts Configuration

## Estado

Aceptado el 2026-05-07.

## Contexto

A Gradle multi-module build con 10+ subprojects (`pkg:*`, `sdks:*`) needs consistent configuration: Java 21 toolchain (`--release 21`), UTF-8 encoding, JUnit 5 test runner, JaCoCo coverage, dependency catalog usage, y eventually shadow JAR packaging para application modules. Without un sharing mechanism, each `build.gradle.kts` duplicates these blocks, y un change (e.g., upgrading JUnit 5 version) requires editing every module.

Gradle provides two sharing mechanisms: parent project (`subprojects {}` o `allprojects {}` blocks en la root `build.gradle.kts`) y convention plugins (separate Gradle build en `build-logic/` que produces plugins consumed por subprojects). La `subprojects {}` approach es simpler initially pero has known problems a scale: it applies configuration un todos subprojects even when specific subprojects need different configuration, y it makes IDE navigation difficult (subproject-specific configuration es en la parent, no en la subproject).

## Decisión

Define todos shared build configuration como convention plugins en `build-logic/`. La plugins are: `riskplatform.java-conventions` (toolchain, encoding, base Java config), `riskplatform.library-conventions` (extends java-conventions, adds JUnit 5, JaCoCo, AssertJ), `riskplatform.app-conventions` (extends library-conventions, adds shadow JAR para fat JAR packaging), `riskplatform.testing-conventions` (extends library-conventions, adds Testcontainers y testing-specific dependencies). Subprojects declare only la applicable plugin ID en their `build.gradle.kts` — typically `id("riskplatform.library-conventions")` para `pkg/*` modules.

## Alternativas consideradas

### Opción A: Convention plugins en build-logic/ composite build (elegida)
- **Ventajas**: Subproject `build.gradle.kts` es 2-5 lines — minimal y readable; adding un new `pkg/*` module requires only `include("pkg:new-module")` en `settings.gradle.kts` y un 2-line `build.gradle.kts`; convention plugins son type-safe Kotlin — IDE autocompletion works; changing un convention (e.g., enabling `--enable-preview`) requires one edit en `build-logic/`, no N edits; `build-logic/` es itself un Gradle build — convention plugins can have their own tests.
- **Desventajas**: `build-logic/` es un additional Gradle build que requires understanding la composite build concept; Kotlin DSL errors en convention plugins can be confusing; first-time contributors need un know un look en `build-logic/` para conventions, no en `build.gradle.kts`.
- **Por qué se eligió**: La investment pays off después de la third subproject. With 10 `pkg/*` modules, la alternative (inline configuration) means 10 copies de la toolchain block.

### Opción B: subprojects {} block en root build.gradle.kts
- **Ventajas**: Simpler un understand; todos configuration en one file; no composite build concept required.
- **Desventajas**: Configuration applies un todos subprojects — individual subprojects cannot easily opt out; IDE navigation shows configuration en la root, no en la subproject; Gradle deprecates `subprojects {}` usage en favor de convention plugins como de Gradle 8; la root `build.gradle.kts` grows proportionally un la number de configuration concerns.
- **Por qué no**: Gradle 8 guidance explicitly recommends convention plugins sobre `subprojects {}`. La degradation a scale (10+ modules) es well-documented.

### Opción C: buildSrc directory (pre-convention plugin approach)
- **Ventajas**: Older pero still supported; similar un `build-logic/` en que it produces plugins; automatically included en every Gradle build.
- **Desventajas**: `buildSrc` es always included regardless de whether convention plugins son used — slows configuration time even if no plugins son applied; cannot be excluded desde specific builds; Gradle recommends migrating desde `buildSrc` un composite builds; menos composable.
- **Por qué no**: `build-logic/` como un composite build es la current Gradle best practice. `buildSrc` es being phased out.

### Opción D: Inline configuration en each build.gradle.kts (no sharing)
- **Ventajas**: Maximum explicitness — every module's build file es self-contained.
- **Desventajas**: 10+ copies de identical toolchain configuration; one version bump requires N edits; copy-paste errors introduce configuration drift.
- **Por qué no**: Configuration duplication a scale es la exact problem convention plugins solve.

## Consecuencias

### Positivo
- `pkg:resilience/build.gradle.kts` es 3 lines: `plugins { id("riskplatform.library-conventions") }` plus dependencies.
- Toolchain upgrade desde Java 21 a Java 25 (cuando el tooling lo soporte) requires one edit en `build-logic/riskplatform.java-conventions.gradle.kts`.
- Convention plugins son versioned con la repository — no external plugin registry dependency.

### Negativo
- Developers unfamiliar con Gradle composite builds may no find `build-logic/` when looking para configuration.
- `build-logic/` adds ~3 seconds un la initial Gradle configuration phase.

### Mitigaciones
- `BUILDING.md` documents la two-build structure y explains `build-logic/`.
- Convention plugin names (`riskplatform.*-conventions`) son self-documenting.

## Validación

- `./gradlew :pkg:resilience:build` succeeds using only `id("riskplatform.library-conventions")` en la module's `build.gradle.kts`.
- Removing un dependency desde `riskplatform.library-conventions` cascades un todos `pkg/*` modules en next build.
- `./gradlew build --configuration-cache` reports cache hit en second run.

## Relacionado

- [[0019-gradle-kotlin-dsl]]
- [[0020-pkg-shared-modules]]

## Referencias

- Gradle Convention Plugins sample: https://docs.gradle.org/current/samples/sample_convention_plugins.html
- BUILDING.md: `BUILDING.md`
