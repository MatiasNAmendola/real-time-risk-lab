---
title: Meta-Coverage — 4 ejes (test/doc/CLI/primitive)
tags: [concept, methodology/coverage, meta]
created: 2026-05-12
source_archive: docs/21-meta-coverage.md (migrado 2026-05-12, Fase 3 Round 2, split)
---

# Meta-Coverage

## Por qué

Test coverage es lo que mide casi todo el mundo. Doc coverage, CLI coverage y primitive coverage son lo que distingue un sistema mantenible de uno frágil. Cuando entrás a un repo nuevo, los primeros tres son lo que revela si vas a poder operarlo o si vas a sufrirlo.

## Los cuatro ejes

### Eje 1: Test coverage (lo conocido)

JaCoCo para Java, `go test -cover` para Go, `coverage.py` para Python. Cubierto por `tests/*` y `atdd-*` — ver docs 11 y 12.

### Eje 2: Doc coverage

- **Wikilink integrity**: ningún link roto en el vault de Obsidian.
- **README presence**: cada directorio relevante tiene README.
- **ADR completeness**: cada decisión documentada con Context, Decision, Alternatives considered, Consequences.
- **Doc-to-code links**: cada doc apunta a paths reales del repo (via `source:` frontmatter o sección Related).
- **Frontmatter validity**: cada nota del vault tiene al menos `title` y `tags`.

Script: `.ai/scripts/coverage-audit.py docs`

### Eje 3: CLI coverage

- **Script inventory**: qué scripts `.sh` existen en el repo.
- **--help support**: cada script es auto-documentado (responde `--help` con información útil).
- **Structured output**: los scripts producen archivos auditables bajo `out/`.
- **Master CLI presence**: existe un único entry point (`./nx`) que unifica todos los sub-comandos.

Script: `.ai/scripts/coverage-audit.py cli`

### Eje 4: Primitive coverage

- **Frontmatter validity**: skills tienen `name`, `intent`, `inputs`, `preconditions`, `postconditions`, `related_rules`, `tags`.
- **Workflow a skill linkage**: cada workflow referencia skills que existen realmente.
- **Rule a test enforcement**: cada rule tiene un test ArchUnit o equivalente assertable.
- **Hook a event wiring**: cada hook declara su evento y está registrado en `.claude/settings.json`.
- **Adapter completeness**: cada adapter tiene README + install.sh.
- **Activation rate**: el skill router realmente activa skills en tool calls (medido via logs).

Script: `.ai/scripts/coverage-audit.py primitives`

## Cross-axis matrix

La tabla unificada que cruza áreas del repo con los cuatro ejes. Generada por `coverage-audit.py all`.

Ejemplo de lectura:
- Un área con `tests: n/a, docs: 100%, cli: n/a, primitives: 80%` es documentación + primitivas sin código que testear.
- Un área con `tests: 52%, docs: 0%, cli: 0%, primitives: 0%` tiene código testeado pero está invisible para operar.

## Interpretar coverage por suite vs agregado

Una trampa frecuente al leer JaCoCo: tomar el % de UNA suite como si fuera el coverage del repo. NO es. Cada test type tiene un scope distinto:

| Suite | Cubre | NO cubre |
|---|---|---|
| Cucumber-JVM (ATDD bare) | domain, application.usecase, infrastructure adapters in-process | HTTP controller, main, factory, mappers vacíos |
| JUnit unit (smoke tests) | HTTP controller, main, factory, adapters concretos | rules deep tree, integración con Postgres real |
| Integration (Testcontainers) | adapters contra DB/Kafka/Floci reales (ADR-0042) | use case puro, rules sin DB |
| Karate ATDD (Vertx) | full stack via HTTP, event bus, Kafka publish | bare-javac path |

Si mirás solo Cucumber sobre `poc/no-vertx-clean-engine/` vas a ver 44% — engañoso. El package `infrastructure.controller` aparece al 0% no porque esté sin testear, sino porque Cucumber no entra por HTTP — el HTTP controller se cubre con `HttpControllerSmokeTest` (JUnit), `infrastructure.repository.feature` con Integration (Testcontainers), y así.

### El número que importa: aggregated cross-suite

JaCoCo soporta merge de `.exec` files. Una vez que hayas corrido todas las suites, el comando `./nx test --coverage` (equivalente a `./gradlew jacocoAggregateReport`) hace:

1. Recolecta `cucumber-jacoco.exec` + `junit-jacoco.exec` + `integration-jacoco.exec` de todos los submódulos.
2. Mergea con la task `JacocoReport` de Gradle (cross-module).
3. Genera reporte HTML en `build/reports/jacoco/aggregate/index.html` (copiado a `out/coverage/latest/`).

El número agregado refleja la realidad. Típicamente sube de 44% a ~80% en `poc/no-vertx-clean-engine/` cuando las tres suites corren.

### El reframe Cucumber-44%-vs-aggregate-80%

Si un reviewer presenta un reporte de Cucumber al 44% como "evidencia" de bajo coverage, la respuesta correcta es un **reframe**, no una postura defensiva:

> "44% es la slice de Cucumber sola. Cucumber valida comportamiento de negocio via use case directo, no via HTTP. El controller, el main, el factory son scope de JUnit unit + integration con Testcontainers. El agregado cross-suite es ~80%. La métrica que importa para ATDD es % de comportamiento de negocio cubierto: ahí `domain.rule` está al 100%, `application.usecase.risk` al 93%. El riesgo regulatorio está cubierto."

Ver también `vault/05-Methodology/Architecture-Question-Bank.md` G5 para el análisis completo de esta pregunta.

## Cómo interpretar los resultados

- **>90%**: salud excelente. Probablemente sobre-medís.
- **70-90%**: realista. Hay gaps específicos identificables.
- **<70%**: alerta. Algún eje está abandonado.

NO trates 100% como meta absoluta. Doc 100% significa que estás documentando trivialidades. Primitive 100% significa que tenés primitiva para todo y eso es overengineering.

## Key Principle

> "Test coverage es lo que mide casi todo el mundo. Doc coverage, CLI coverage y primitive coverage son lo que distingue un sistema mantenible de uno frágil. Cuando entrás a un repo nuevo, los primeros tres son lo que revela si vas a poder operarlo o si vas a sufrirlo."

## Anti-patterns

- **Coverage como meta única**: 100% en un eje pero el sistema no funciona = vanity metric.
- **Métricas sin acción**: si un audit reporta gaps pero nadie los lee, es caro e inútil.
- **Audit ad-hoc**: si no hay un comando que cualquiera corra (`./nx audit`), el audit no se hace.
- **Solo test coverage**: un repo con 90% test coverage pero sin README, sin --help, y con primitivas sin wiring es un repo que nadie puede operar excepto quien lo escribió.

## Tools recomendados (opcionales)

Estos tools se invocan automáticamente si están instalados. Si no, el audit funciona igual sin ellos.

- **lychee**: link checker rápido en Rust. `brew install lychee`. Valida links externos en docs.
- **markdownlint-cli**: validación de estilo Markdown. `brew install markdownlint-cli`.
- **vale**: prose linter, verifica consistencia de vocabulario técnico. `brew install vale`.

Si están instalados, `coverage-audit.py docs` los invoca y suma sus findings al reporte.

## Related

- [[Primitive-Coverage-Ratio]]
- [[ATDD]]
- [[Risk-Platform-Overview]]
