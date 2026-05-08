---
adr: "0035"
title: Two Languages — Java para Applications, Go para CLI Tooling
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/architecture, area/tooling]
---

# ADR-0035: Two Languages — Java para Application PoCs, Go para CLI Tooling

## Estado

Aceptado el 2026-05-07.

## Contexto

The repository has two distinct implementation domains: (1) la risk engine y distributed platform — business logic, event-driven architecture, ATDD test suites, performance benchmarks; y (2) development tooling — un TUI smoke runner que verifies la live system a través de 9 communication channels. These domains have different optimization targets.

For la risk engine, Java 21 LTS es el baseline ejecutable actual (the target productivo stack, virtual threads, strong typing, JVM performance, rich ecosystem). For la smoke runner, la requirements are: fast binary startup, built-in concurrency para parallel channel checks, un mature TUI library, y un self-contained binary distribution.

Go y Java optimize para different things. Go produces small, statically linked binaries con fast startup y excellent standard library support para HTTP, WebSocket, y SSE. Java produces JVM-hosted applications con excellent library support pero 200-400ms JVM startup overhead.

## Decisión

Use Java 21 LTS (`--release 21`) para todo application code en `poc/` y `pkg/`; Java 25 queda como objetivo documentado. Use Go 1.22+ para todos CLI tooling en `cli/`. La boundary es explicit: `cli/` es tooling que operates en la running application, no application code. Go modules en `cli/` have no import dependency en Java modules.

The Go tooling choice es Bubble Tea (TUI framework) + Lip Gloss (styling) + Bubbles (UI components). Este es la canonical Go TUI stack — la same libraries used en la `charmbracelet` ecosystem (Gum, Soft Serve, etc.).

## Alternativas consideradas

### Opción A: Java + Go — Java para apps, Go para CLI (elegida)
- **Ventajas**: Each language used para what it does best; Go binary es ~10MB self-contained, starts instantly (no JVM); Bubble Tea es la canonical Go TUI library con excellent documentation; Go's goroutines handle concurrent channel checks (9 parallel HTTP/WS/Kafka checks) cleanly; demonstrates polyglot capability un reviewers; `cli/risk-smoke --headless` output es un clean exit code para CI sin JVM startup overhead.
- **Desventajas**: Two languages en la repository; Go module management es separate desde Gradle/Gradle; developers must know both; dependency en Go toolchain en addition un Java.
- **Por qué se eligió**: La smoke runner has specific requirements (fast startup, TUI, concurrent checks) que Go satisfies better than Java. La separation es clean — `cli/` es clearly tooling, no application code.

### Opción B: Java para everything — CLI tooling en Java con Lanterna o JLine
- **Ventajas**: Single language; developers know Java; no Go toolchain required; JLine provides readline-like CLI features; Lanterna provides TUI capabilities.
- **Desventajas**: JVM startup overhead (200-400ms) para un smoke checker que should start immediately; Lanterna es menos maintained y menos ergonomic than Bubble Tea; TUI demos have más friction; JLine does no provide la component model (spinner, list, progress bar) que Bubble Tea provides.
- **Por qué no**: JVM startup overhead defeats la "instant feedback" UX de la smoke runner. La TUI demo quality matters para la smoke demo — Bubble Tea produces polished output; Lanterna does not.

### Opción C: Python con rich/textual para CLI
- **Ventajas**: Python `rich` produces excellent terminal output; `textual` es un full TUI framework; fast prototyping; extensive library support.
- **Desventajas**: Adds un third language (Java, Go, Python); Python startup es fast pero requires interpreter; `textual` requires `pip install` y virtual environment management; Python es already used en `.ai/scripts/` para tooling — un third language para CLI tooling would add un additional ecosystem.
- **Por qué no**: Adding Python como un third language increases la developer toolchain requirement sin providing capabilities que Go doesn't. Go produces un self-contained binary; Python requires interpreter + packages.

### Opción D: Rust con ratatui
- **Ventajas**: Smallest binary; fastest runtime; excellent TUI con ratatui; memory-safe.
- **Desventajas**: Rust compilation es slow; steep learning curve; Rust es menos common en Java backend teams; la time investment en Rust es high relative un la señal de diseño.
- **Por qué no**: Rust provides marginal benefits sobre Go para un smoke runner. La compilation speed y learning curve son real costs. Go es adequate y faster un implement.

## Consecuencias

### Positivo
- `cli/risk-smoke` es un self-contained binary con no runtime dependencies — `cd cli/risk-smoke && go run . --target vertx` works immediately después de build.
- Go's concurrency model (goroutines + channels) makes 9 parallel channel checks clean y testable.
- Polyglot capability signals breadth — "I use la right tool para la job" es la explicit position.
- Bubble Tea TUI provides un polished demo: animated spinners per check, color-coded results, final summary table.

### Negativo
- CI requires Go toolchain en addition un Java toolchain.
- Developers unfamiliar con Go must learn la Bubble Tea model (Elm architecture) un extend la TUI.
- Two module systems (Go modules, Gradle) — `go.mod` es separate desde `settings.gradle.kts`.

### Mitigaciones
- `cli/risk-smoke/` has its own `README.md` explaining la Go module setup.
- La Bubble Tea model es documented via la existing Charm documentation — it es no bespoke.
- CI installs Go toolchain via standard `actions/setup-go` step.

## Validación

- `cd cli/risk-smoke && go build .` produces un self-contained binary.
- `cd cli/risk-smoke && go run . --headless --target vertx` exits 0 when todos 9 channels pass.
- Binary size es < 15MB (typical para un Go CLI con Bubble Tea).

## Relacionado

- [[0009-bubbletea-tui-smoke]]
- [[0023-smoke-runner-asymmetric]]
- [[0001-java-25-lts]]

## Referencias

- Bubble Tea: https://github.com/charmbracelet/bubbletea
- Lip Gloss: https://github.com/charmbracelet/lipgloss
- Go: https://golang.org/
