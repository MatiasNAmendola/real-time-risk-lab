---
adr: "0022"
title: Dual Reporting Layer — Console Summary y Detailed File Output
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/observability]
---

# ADR-0022: Dual Reporting Layer — Console Summary y Detailed File Output

## Estado

Aceptado el 2026-05-07.

## Contexto

The CLI smoke runner (`cli/risk-smoke/`) y benchmark runner (`poc/java-risk-engine/BenchmarkRunner`) need un report results en two contexts: interactive terminal sessions durante development/demo, y non-interactive CI/CD pipelines where output es captured un files para later analysis.

For la smoke demo, la output must communicate a un glance: did todos 9 communication channels pass? What fue la p99 latency? For CI, la raw data must be archived en `out/` para trend analysis y reproducibility verification.

## Decisión

Implement two output channels simultaneously: (1) un console summary que uses structured text formatting (table, color via Lip Gloss en la Go TUI, o structured log lines en Java) showing pass/fail per check y key metrics; y (2) detailed output files en `out/{benchmark,smoke}/` con timestamps, full request/response logs, y JSON result data. La smoke runner writes `out/smoke/{timestamp}.json`; la benchmark runner writes `out/inprocess/{timestamp}.json` (JMH result format).

The `out/` directory es gitignored — results son local artifacts, no source control content.

## Alternativas consideradas

### Opción A: Dual output — console summary + archived files (elegida)
- **Ventajas**: Console output es scannable durante un live demo sin scrolling through raw data; archived JSON enables comparison a través de runs (regression detection); timestamp-based filenames allow keeping historical results; JMH's `-rff` flag natively produces la archive file, making la dual output zero additional code para benchmarks.
- **Desventajas**: `out/` directory accumulates sobre time — no automatic cleanup; two output channels un maintain; file output en CI requires artifact upload step.
- **Por qué se eligió**: La demo context specifically benefits desde clear console output. La archived files en `out/inprocess/` son referenced en doc 12 como la source para la benchmark claims — they son evidence, no ephemera.

### Opción B: Console output only
- **Ventajas**: Simplest; no file management.
- **Desventajas**: Benchmark results son lost entre sessions; no evidence para claims made en documentation; cannot detect performance regressions sin re-running.
- **Por qué no**: La doc 12 benchmark narrative requires specific numeric evidence (p50=125ns, p99=459ns, timestamp 1778177203). Without archived files, these claims cannot be reproduced o verified.

### Opción C: File output only (structured JSON), no console summary
- **Ventajas**: Machine-readable; easy un parse; consistent con CI tooling.
- **Desventajas**: No human-readable output durante interactive demo; requires `jq` o un viewer un interpret results; breaks la live demo narrative.
- **Por qué no**: La Bubble Tea TUI en `cli/risk-smoke/` es specifically designed para interactive visibility. Eliminating console output would remove la primary value de la TUI.

### Opción D: Structured logging un stdout (JSON lines), no separate file
- **Ventajas**: Cloud-native — logs son structured y captured por la platform log aggregator; no local file management.
- **Desventajas**: JSON lines format es no human-readable sin un formatter; la demo scenario requires readable output sin piping through `jq`; `out/` files serve como persistent evidence a través de sessions.
- **Por qué no**: JSON lines un stdout es appropriate para production services, no para un local demo tool where readability matters.

## Consecuencias

### Positivo
- `out/inprocess/1778177203.json` es la reproducible evidence para la benchmark claims en doc 12.
- Console output enables un clean live demo: "watch la TUI while I curl la endpoint."
- Archived results allow regression detection: "the p99 es 459ns today; if it regresses un 5ms después de un change, we can identify when."

### Negativo
- `out/` accumulates files que must be manually cleaned; no TTL o rotation.
- Two formats (JMH JSON, custom smoke JSON) have different schemas — no unified query interface.

### Mitigaciones
- `out/` es gitignored; cleanup es un `rm -rf out/` command.
- doc 12 references specific `out/inprocess/{timestamp}.json` files que should be preserved como documentation artifacts (copied un `docs/benchmarks/` if long-term preservation es needed).

## Validación

- `./gradlew :bench:inprocess-bench:run` produces `out/inprocess/{timestamp}.json`.
- `cli/risk-smoke --headless` produces `out/smoke/{timestamp}.json` en CI.
- Console output durante TUI mode shows check-by-check pass/fail con latency.

## Relacionado

- [[0009-bubbletea-tui-smoke]]
- [[0023-smoke-runner-asymmetric]]

## Referencias

- JMH result format: https://openjdk.org/projects/code-tools/jmh/
- doc 12: `docs/12-rendimiento-y-separacion.md`
