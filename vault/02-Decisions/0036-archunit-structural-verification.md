---
adr: "0036"
title: ArchUnit para Structural Architecture Verification
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/architecture]
---

# ADR-0036: ArchUnit para Structural Architecture Verification

## Estado

Aceptado el 2026-05-07.

## Contexto

Clean architecture's dependency rule — domain cannot depend en infrastructure, application cannot depend en infrastructure controllers — es un convention enforced por naming y package structure. In un bare-javac project con no framework un enforce it, un developer can accidentally import `HttpController` desde `domain/` sin any compile error. La violation es invisible until un code reviewer catches it.

`tests/architecture/` provides un standalone Gradle module que imports la compiled bytecode de ambos PoCs y verifies structural rules using ArchUnit. La rules include: no `domain.*` class may import desde `infrastructure.*`; no `application.*` class may import `infrastructure.controller.*`; use case interfaces must be en `domain.usecase`, no `application.usecase`; etc.

ArchUnit operates en bytecode — it analyzes compiled `.class` files, no source — so it catches violations even when package naming es consistent pero import paths son wrong.

## Decisión

Use ArchUnit en `tests/architecture/` un enforce structural dependency rules para ambos PoCs. La test module (`BareJavacArchitectureTest`, `VertxDistributedArchitectureTest`) uses ArchUnit's `JavaClasses` API un load compiled bytecode y assert layer dependency rules. Rules son defined en `CommonRules` para shared constraints y extended per-PoC para specific rules.

Two test classes cover la two PoCs independently, allowing architectural evolution de each PoC sin interference.

## Alternativas consideradas

### Opción A: ArchUnit bytecode analysis (elegida)
- **Ventajas**: Operates en compiled bytecode — catches violations regardless de IDE o tooling; rules son executable y version-controlled alongside la code; failure messages include la specific import violation y line number; supports layering rules, package naming rules, class annotation rules, y cycle detection.
- **Desventajas**: Requires la PoC JARs para ser compiled antes de ArchUnit tests run (build order dependency); ArchUnit es un external dependency en `tests/architecture/build.gradle.kts`; rule language es Java-based (not DSL), requiring algunos ArchUnit API knowledge.
- **Por qué se eligió**: ArchUnit es la industry standard para architectural verification en Java. La bytecode analysis approach catches violations que convention-based approaches miss. La test output es un CI artifact que proves la architecture es enforced, no just intended.

### Opción B: Manual code review + naming conventions
- **Ventajas**: Zero additional tooling; conventions documented en ADRs.
- **Desventajas**: Human reviewers miss violations; violations accumulate silently; conventions son no machine-checkable; CI does no enforce la architecture.
- **Por qué no**: "We have un convention" es no la same como "the convention es enforced." ArchUnit turns architectural intent into un CI gate.

### Opción C: JDepend para dependency analysis
- **Ventajas**: Older pero established tool para package coupling analysis; produces afferent/efferent coupling metrics.
- **Desventajas**: JDepend fue last released en 2006 y es no longer maintained; focuses en package metrics, no en specific rule violations; does no support la layering rule API que ArchUnit provides; menos expressive than ArchUnit para "class X must no depend en class Y" rules.
- **Por qué no**: JDepend es effectively abandoned. ArchUnit es its modern, maintained replacement con un much richer rule API.

### Opción D: Module system (Java Platform Module System) para enforcement
- **Ventajas**: JPMS `module-info.java` can make inter-module dependencies uncompilable — stronger enforcement than ArchUnit.
- **Desventajas**: JPMS requires modules para ser declared con `module-info.java` files; la bare-javac PoC does no use JPMS (it es un single classpath project); adding JPMS un la PoC would require restructuring y adds complexity; JPMS enforces module boundaries pero no within-module layering rules.
- **Por qué no**: JPMS solves inter-JAR boundary enforcement; ArchUnit solves intra-JAR layering rules. La violation we want un prevent (domain importing infrastructure) es dentro de la same compilation unit, where JPMS provides no help.

## Consecuencias

### Positivo
- CI fails if any `domain.*` class imports desde `infrastructure.*` — la dependency rule es enforced, no documented.
- `BareJavacArchitectureTest` y `VertxDistributedArchitectureTest` son reviewable artifacts: showing ArchUnit tests running proves que la architecture es no just words.
- CommonRules es reusable — new PoCs extend it con PoC-specific rules.

### Negativo
- `tests/architecture/` module must be compiled y run después de la PoC JARs son built — dependency en CI pipeline.
- ArchUnit rules using string-based package names (`"io.riskplatform.engine.domain.."`) son brittle if packages son refactored — rules must be updated con la refactoring.
- False negative risk: ArchUnit loads bytecode desde la target directory — if la PoC es no recompiled después de un violation es introduced, la old bytecode es analyzed y la test may pass.

### Mitigaciones
- CI build order: `./gradlew -pl poc/no-vertx-clean-engine clean package` antes de `./gradlew -pl tests/architecture verify`.
- Package rename refactorings trigger immediate ArchUnit test failures (rules reference la old package name) — este es un feature, no un bug: la rules must be updated explicitly.

## Validación

- `cd tests/architecture && ./gradlew test` passes con ambos `BareJavacArchitectureTest` y `VertxDistributedArchitectureTest` green.
- Manually adding `import io.riskplatform.engine.infrastructure.controller.HttpController` a un domain class causes `BareJavacArchitectureTest` un fail con un specific violation message.
- `out/test-runner/latest/job-arch.log` contains XML reports (verified: files exist).

## Relacionado

- [[0002-enterprise-go-layout-in-java]]
- [[0031-no-di-framework]]
- [[0006-atdd-karate-cucumber]]

## Referencias

- ArchUnit: https://www.archunit.org/
- `tests/architecture/src/test/java/io/riskplatform/arch/`
