---
title: "21 — Meta-coverage: cobertura mas alla de tests"
tags: [coverage, docs, cli, primitives, audit, meta]
---

# 21 — Meta-coverage: cobertura mas alla de tests

## Por que este doc

Test coverage es lo que mide casi todo el mundo. Doc coverage, CLI coverage y primitive coverage son lo que distingue un sistema mantenible de uno fragil. Cuando entras a un repo nuevo, los primeros tres son lo que revela si vas a poder operarlo o si vas a sufrirlo.

## Los cuatro ejes

### Eje 1: Test coverage (lo conocido)

JaCoCo para Java, `go test -cover` para Go, `coverage.py` para Python. Cubierto por `tests/*` y `atdd-*` — ver docs 11 y 12.

### Eje 2: Doc coverage

- **Wikilink integrity**: ningun link roto en el vault de Obsidian.
- **README presence**: cada directorio relevante tiene README.
- **ADR completeness**: cada decision documentada con Context, Decision, Alternatives considered, Consequences.
- **Doc-to-code links**: cada doc apunta a paths reales del repo (via `source:` frontmatter o seccion Related).
- **Frontmatter validity**: cada nota del vault tiene al menos `title` y `tags`.

Script: `.ai/scripts/coverage-audit.py docs`

### Eje 3: CLI coverage

- **Script inventory**: que scripts `.sh` existen en el repo.
- **--help support**: cada script es auto-documentado (responde `--help` con informacion util).
- **Structured output**: los scripts producen archivos auditables bajo `out/`.
- **Master CLI presence**: existe un unico entry point (`./nx`) que unifica todos los sub-comandos.

Script: `.ai/scripts/coverage-audit.py cli`

### Eje 4: Primitive coverage

- **Frontmatter validity**: skills tienen `name`, `intent`, `inputs`, `preconditions`, `postconditions`, `related_rules`, `tags`.
- **Workflow a skill linkage**: cada workflow referencia skills que existen realmente.
- **Rule a test enforcement**: cada rule tiene un test ArchUnit o equivalente assertable.
- **Hook a event wiring**: cada hook declara su evento y esta registrado en `.claude/settings.json`.
- **Adapter completeness**: cada adapter tiene README + install.sh.
- **Activation rate**: el skill router realmente activa skills en tool calls (medido via logs).

Script: `.ai/scripts/coverage-audit.py primitives`

## Cross-axis matrix

La tabla unificada que cruza areas del repo con los cuatro ejes. Generada por `coverage-audit.py all`.

Ejemplo de lectura:
- Un area con `tests: n/a, docs: 100%, cli: n/a, primitives: 80%` es documentacion + primitivas sin codigo que testear.
- Un area con `tests: 52%, docs: 0%, cli: 0%, primitives: 0%` tiene codigo testeado pero esta invisible para operar.

## Como correr cada audit

```bash
# Solo docs
python3 .ai/scripts/coverage-audit.py docs

# Solo primitives
python3 .ai/scripts/coverage-audit.py primitives

# Solo CLI
python3 .ai/scripts/coverage-audit.py cli

# Todo + cross-axis
python3 .ai/scripts/coverage-audit.py all

# JSON estructurado
python3 .ai/scripts/coverage-audit.py all --json

# Reporte Markdown listo para pegar
python3 .ai/scripts/coverage-audit.py all --report-md > out/coverage-audit/latest/full.md

# Strict mode (exit 1 si algun eje < 70%)
python3 .ai/scripts/coverage-audit.py all --strict
```

## Como interpretar los resultados

- **>90%**: salud excelente. Probablemente sobre-medis.
- **70-90%**: realista. Hay gaps especificos identificables.
- **<70%**: alerta. Algun eje esta abandonado.

NO trates 100% como meta absoluta. Doc 100% significa que estas documentando trivialidades. Primitive 100% significa que tenes primitiva para todo y eso es overengineering.

## Key Principle

> "Test coverage es lo que mide casi todo el mundo. Doc coverage, CLI coverage y primitive coverage son lo que distingue un sistema mantenible de uno fragil. Cuando entras a un repo nuevo, los primeros tres son lo que revela si vas a poder operarlo o si vas a sufrirlo."

## Tools recomendados (opcionales)

Estos tools se invocan automaticamente si estan instalados. Si no, el audit funciona igual sin ellos.

- **lychee**: link checker rapido en Rust. `brew install lychee`. Valida links externos en docs.
- **markdownlint-cli**: validacion de estilo Markdown. `brew install markdownlint-cli`.
- **vale**: prose linter, verifica consistencia de vocabulario tecnico. `brew install vale`.

Si estan instalados, `coverage-audit.py docs` los invoca y suma sus findings al reporte.

## Anti-patterns

- **Coverage como meta unica**: 100% en un eje pero el sistema no funciona = vanity metric.
- **Metricas sin accion**: si un audit reporta gaps pero nadie los lee, es caro e inutil.
- **Audit ad-hoc**: si no hay un comando que cualquiera corra (`./nx audit`), el audit no se hace.
- **Solo test coverage**: un repo con 90% test coverage pero sin README, sin --help, y con primitivas sin wiring es un repo que nadie puede operar excepto quien lo escribio.

## Interpretar coverage por suite vs agregado

Una trampa frecuente al leer JaCoCo: tomar el % de UNA suite como si fuera el coverage del repo. NO es. Cada test type tiene un scope distinto:

| Suite | Cubre | NO cubre |
|---|---|---|
| Cucumber-JVM (ATDD bare) | domain, application.usecase, infrastructure adapters in-process | HTTP controller, main, factory, mappers vacios |
| JUnit unit (smoke tests) | HTTP controller, main, factory, adapters concretos | rules deep tree, integration con Postgres real |
| Integration (Testcontainers) | adapters contra DB/Kafka/Floci reales (ADR-0042) | use case puro, rules sin DB |
| Karate ATDD (Vertx) | full stack via HTTP, event bus, Kafka publish | bare-javac path |

Si miras solo Cucumber sobre `poc/no-vertx-clean-engine/` vas a ver 44% — enganoso. El package `infrastructure.controller` aparece al 0% no porque este sin testear, sino porque Cucumber no entra por HTTP — el HTTP controller se cubre con `HttpControllerSmokeTest` (JUnit), `infrastructure.repository.feature` con Integration (Testcontainers), y asi.

### El numero que importa: aggregated cross-suite

JaCoCo soporta merge de `.exec` files. Una vez que hayas corrido todas las suites, el comando `./nx test --coverage` (equivalente a `./gradlew jacocoAggregateReport`) hace:

1. Recolecta `cucumber-jacoco.exec` + `junit-jacoco.exec` + `integration-jacoco.exec` de todos los submodulos.
2. Mergea con la task `JacocoReport` de Gradle (cross-module).
3. Genera reporte HTML en `build/reports/jacoco/aggregate/index.html` (copiado a `out/coverage/latest/`).

Para ver el reporte directamente: `./nx audit coverage`.

El numero agregado refleja la realidad. Tipicamente sube de 44% a ~80% en `poc/no-vertx-clean-engine/` cuando las tres suites corren.

### How to explain the coverage split

If a reviewer presents a Cucumber report at 44% as "evidence" of low coverage, the correct response is a **reframe**, not a defensive posture:

> "44% es la slice de Cucumber sola. Cucumber valida comportamiento de negocio via use case directo, no via HTTP. El controller, el main, el factory son scope de JUnit unit + integration con Testcontainers. El agregado cross-suite es ~80%. La metrica que importa para ATDD es % de comportamiento de negocio cubierto: ahi `domain.rule` esta al 100%, `application.usecase.risk` al 93%. El riesgo regulatorio esta cubierto."

See also `docs/09-architecture-question-bank.md` G5 for the full analysis of this question.

## Coverage en terminal: tabla + Markdown

El comando `./nx test --coverage` ahora imprime tabla al terminal + Markdown summary + HTML, todo en `out/coverage/<ts>/`. El numero TOTAL es el agregado cross-suite cross-module — el que refleja la realidad del coverage del sistema.

```bash
# Correr con coverage y ver tabla al instante
./nx test --coverage

# Solo parsear un jacoco.xml existente
python3 .ai/scripts/coverage-summary.py build/reports/jacoco/aggregate/jacoco.xml

# JSON estructurado
python3 .ai/scripts/coverage-summary.py build/reports/jacoco/aggregate/jacoco.xml --json

# Markdown (para pegar en PR o doc)
python3 .ai/scripts/coverage-summary.py build/reports/jacoco/aggregate/jacoco.xml --markdown

# Fallar si TOTAL < 80%
python3 .ai/scripts/coverage-summary.py build/reports/jacoco/aggregate/jacoco.xml --threshold 80
```

Los outputs por corrida se guardan en `out/coverage/<timestamp>/`:
- `html/index.html` — reporte JaCoCo completo
- `summary.md` — tabla Markdown lista para pegar
- `out/coverage/latest` — symlink a la ultima corrida

## Related

- Doc 11: ATDD con Karate y Cucumber
- Doc 14: Primitive usage retro
- Doc 15: Script output audit
- `.ai/scripts/coverage-audit.py`: implementacion del framework de audit multi-eje
- `.ai/scripts/coverage-summary.py`: parser jacoco.xml -> tabla terminal + Markdown + JSON
- `.ai/scripts/test_coverage_audit.py`: tests del framework de audit
- `.ai/scripts/test_coverage_summary.py`: tests del parser jacoco.xml
- `scripts/test-all.sh`: suite `coverage_audit` integrada al orchestrador de tests
