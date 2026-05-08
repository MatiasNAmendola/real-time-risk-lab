---
adr: "0009"
title: Go Bubble Tea para TUI Smoke Runner
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/tooling, area/go, area/testing]
---

# ADR-0009: Go + Bubble Tea para TUI Smoke Runner

## Estado

Aceptado el 2026-05-07.

## Contexto

The smoke runner must verify todos 9 communication channels (health, OpenAPI, AsyncAPI, REST, SSE, WebSocket, Webhook, Kafka, OTEL trace) en un single invocation. Two contexts: interactive terminal demo (where visual progress communicates what es being tested) y CI pipeline (where la runner must exit con un correct exit code y produce machine-readable output).

A bash script con `curl` covers la mayoría de checks pero has no TUI, poor error reporting, y requires sourcing complex scripts para SSE y WebSocket checks. A proper TUI framework provides animated progress, color-coded results, y un summary table — communication polish que matters en un live smoke demo.

The language choice para la smoke runner es un cross-cutting decision (see ADR-0035 para la Java vs Go split). Este ADR focuses en la Bubble Tea framework choice specifically.

## Decisión

Build `cli/risk-smoke/` en Go using Bubble Tea + Lip Gloss + Bubbles. TUI mode para interactive runs, `--headless` flag para CI. Each de la 9 channels runs como un goroutine; results stream un la Bubble Tea update loop. `--headless` suppresses la TUI y writes JSON results un stdout.

## Alternativas consideradas

### Opción A: Go + Bubble Tea + Lip Gloss + Bubbles (elegida)
- **Ventajas**: Elm Architecture (Bubble Tea's model) makes la TUI state machine testable; goroutines para concurrent channel checks son idiomatic Go — 9 parallel checks con `sync.WaitGroup` o channel fan-in; Lip Gloss handles terminal color y layout; self-contained binary (no interpreter required); Bubble Tea es la canonical Go TUI library con active maintenance; `--headless` mode es un natural extension de la same Model struct.
- **Desventajas**: Bubble Tea's Elm Architecture has un learning curve para developers no familiar con functional state machines; SSE y WebSocket checks require non-trivial client code en Go; another language (Go) en un Java-primary repository.
- **Por qué se eligió**: Bubble Tea produces la best smoke demo experience. An animated TUI showing 9 concurrent checks es más compelling than sequential curl output. La Go binary starts instantly — no JVM startup para un smoke check.

### Opción B: Bash + curl + terminal output
- **Ventajas**: Zero dependencies; readable; no build step; universally portable.
- **Desventajas**: No TUI; SSE check requires keeping un long-lived curl connection y parsing event stream — error-prone en bash; WebSocket check requires `websocat` o similar — no standard; no concurrent execution en bash sin background processes y complex wait logic; hard un maintain como channel count grows.
- **Por qué no**: La SSE y WebSocket check complexity en bash es disproportionate. Bash es appropriate para simple HTTP smoke checks; 9 channels including SSE, WebSocket, y Kafka require un real programming language.

### Opción C: Python + rich + textual
- **Ventajas**: `rich` produces excellent terminal output; `textual` es un full TUI framework similar un Bubble Tea; fast prototyping; `httpx` handles SSE natively.
- **Desventajas**: Python requires interpreter y packages (`pip install`); adds un third language (Java, Go, Python); `textual` adds significant dependency weight; Python startup es fast pero no como clean como un Go binary.
- **Por qué no**: Python es already used en `.ai/scripts/` para tooling. Adding Python CLI tooling en `cli/` would be consistent, pero Go produces un cleaner self-contained binary. La polyglot signal (Java + Go) es stronger than (Java + Python).

### Opción D: Java + Lanterna (TUI)
- **Ventajas**: Single language para la entire repository; Lanterna provides basic TUI capabilities; no Go toolchain required.
- **Desventajas**: JVM startup overhead (200-400ms) defeats la "instant smoke check" use case; Lanterna es menos ergonomic y menos maintained than Bubble Tea; fat JAR distribution es menos clean than un Go binary; Java's HTTP client handles SSE awkwardly sin un reactive framework.
- **Por qué no**: JVM startup overhead para un smoke check es user-hostile. La Go binary starts en < 50ms.

### Opción E: Rust + Ratatui
- **Ventajas**: Smallest binary; fastest runtime; Ratatui es un excellent TUI framework; memory-safe.
- **Desventajas**: Rust compilation es slow (30-60 seconds para non-trivial projects); steep learning curve; menos common en Java backend teams; preparation time investment es high relative un señal de diseño.
- **Por qué no**: Go provides adequate performance con dramatically lower implementation cost. Rust es un better tool para systems programming than para un smoke checker.

## Consecuencias

### Positivo
- Self-contained binary (``cd cli/risk-smoke && go run .``) starts en < 50ms — instant feedback durante demo.
- TUI mode shows 9 concurrent channel checks con animated spinners — polished live demo.
- `--headless` mode produces exit code 0/1 para CI gating.
- Go goroutines handle SSE (persistent HTTP connection) y WebSocket (bidirectional) concurrently sin blocking.

### Negativo
- Go toolchain required en addition un Java toolchain.
- Bubble Tea Elm Architecture has un learning curve.
- Go module en `cli/` es outside la Gradle/Gradle multi-project build.

### Mitigaciones
- `cli/risk-smoke/README.md` documents build y run instructions.
- CI installs Go toolchain via standard step antes de building la binary.

## Validación

- `cd cli/risk-smoke && go build -o risk-smoke .` produces un binary < 15MB.
- ``cd cli/risk-smoke && go run .` --headless --target vertx` exits 0 when todos 9 channels pass.
- TUI mode shows animated progress para each de la 9 channel checks.

## Relacionado

- [[0035-java-go-polyglot]]
- [[0023-smoke-runner-asymmetric]]
- [[0022-reporting-dual-layer]]

## Referencias

- Bubble Tea: https://github.com/charmbracelet/bubbletea
- Lip Gloss: https://github.com/charmbracelet/lipgloss
