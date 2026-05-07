# risk-smoke

End-to-end smoke runner for the Risk Decision Platform.
Built with [Bubble Tea](https://github.com/charmbracelet/bubbletea) (Elm-style TUI),
[Lip Gloss](https://github.com/charmbracelet/lipgloss) (styles), and
[Bubbles](https://github.com/charmbracelet/bubbles) (spinner/progress components).

---

## What it does

Runs 9 smoke checks against every integration surface of the Risk Engine:

| # | ID          | What is tested                                              |
|---|-------------|-------------------------------------------------------------|
| 1 | `health`    | `GET /healthz` → 200 OK                                     |
| 2 | `openapi`   | `GET /openapi.json` → JSON valid, `webhooks` key present    |
| 3 | `asyncapi`  | `GET /asyncapi.json` → `asyncapi` version 3.x               |
| 4 | `rest`      | `POST /risk` × 5 (amounts 1k–500k ARS) + p50/p99 latency   |
| 5 | `sse`       | `GET /risk/stream` (SSE) → reads up to 3 events in 5s       |
| 6 | `websocket` | `WS /ws/risk` → sends 3 tx, reads ≥3 responses             |
| 7 | `webhook`   | Register local listener → fire DECLINE tx → await callback  |
| 8 | `kafka`     | Redpanda consumer on `risk-decisions` topic, 5 msgs / 5s    |
| 9 | `otel`      | `POST /risk` → `traceresponse` → OpenObserve trace lookup   |

---

## How to run

```bash
# Build
make build

# Interactive TUI
./bin/risk-smoke

# CI / headless (exit 0 = all pass, exit 1 = any fail)
make headless
# or
./bin/risk-smoke --headless

# Run only specific checks
./bin/risk-smoke --only health,rest,kafka

# Custom endpoints
./bin/risk-smoke --base-url http://staging:8080 \
                 --kafka-broker kafka-staging:9092 \
                 --openobserve http://observability:5080
```

---

## Modes

### Interactive TUI
Launches a full-screen dashboard.
- Header: title + global status (green/red).
- Check list: each row shows `icon  name  status  latency`.
- Detail panel: cycle tabs (detail / request / response) with `Tab`.
- Keys: `↑↓` navigate, `enter`/`r` re-run selected, `a` run all, `s` skip, `q` quit.

### Headless (`--headless` / `--ci`)
Plain-text output, no TUI. Prints a line per check and exits with code `0` (all pass) or `1` (any fail). Suitable for GitHub Actions, CI pipelines.

### Filtered (`--only`)
Comma-separated list of check IDs. Only those checks are run (TUI or headless).

---

## Screenshot (textual description)

```
  Risk Engine Smoke Runner  ✗ FAILED (2 pass / 7 fail)

  > Health — GET /healthz                            ✅ passed   42ms
    OpenAPI — validate /openapi.json                 ❌ failed
    AsyncAPI — validate /asyncapi.json               ❌ failed
    REST sync x5 — POST /risk                        ❌ failed
    SSE stream — /risk/stream                        ❌ failed
    WebSocket bidi — /ws/risk                        ❌ failed
    Webhook — register + callback                    ❌ failed
    Kafka — topic risk-decisions                     ❌ failed
    OTEL trace — traceresponse header                ❌ failed

  ╭─────────────────────────────────────────────────╮
  │ [detail]  [request]  [response/error]           │
  │                                                  │
  │ FAILED — missing 'webhooks' key in OpenAPI doc  │
  │ Error: missing 'webhooks' key in OpenAPI doc    │
  ╰─────────────────────────────────────────────────╯

  ↑/↓ navigate  enter/r re-run  a run-all  s skip  tab detail-tab  q quit
```

---

## How to add a new check

1. Create `internal/flows/my_check.go`:

```go
package flows

import "github.com/naranjax/risk-smoke/internal/config"

const CheckMyNew = "mynew"

type MyNewCheck struct{}

func (c *MyNewCheck) ID() string   { return CheckMyNew }
func (c *MyNewCheck) Name() string { return "MyNew — describe what it tests" }

func (c *MyNewCheck) Run(cfg *config.Config) Result {
    // ... run your check, return Result{Passed: true/false, ...}
}
```

2. Register it in `internal/flows/flows.go` inside `All()`:

```go
func All(cfg *config.Config) []Check {
    return []Check{
        // ... existing checks ...
        &MyNewCheck{},
    }
}
```

3. Add a matching `CheckEntry` row in `internal/tui/model.go` inside `buildChecks()`.

That's it — the TUI and headless runner pick it up automatically.

---

## Why Bubble Tea

Bubble Tea implements the [Elm Architecture](https://guide.elm-lang.org/architecture/):
- **Model** — immutable state snapshot.
- **Update(msg) → (Model, Cmd)** — pure function, easy to unit test.
- **View(Model) → string** — pure render, no side effects.

Check runners are plain `tea.Cmd` goroutines that return `tea.Msg` values.
No shared state, no mutexes, no channels to manage in business logic.
The runtime (Bubble Tea) handles all concurrency.

---

## Reports

Each headless run writes a structured report under `out/smoke/`.

```
out/smoke/
├── latest -> 2026-05-07T15-30-00/       # symlink to most recent run
└── 2026-05-07T15-30-00/
    ├── summary.md                        # human-readable table
    ├── summary.txt                       # grep-friendly plain text
    ├── full.log                          # raw log of all checks
    ├── meta.json                         # run metadata (host, exit code, etc.)
    └── checks/
        ├── 01-health.md                  # per-check detail with step table
        ├── 01-health.log                 # raw step log
        └── ...
```

Quick read:
```bash
cat out/smoke/latest/summary.md
cat out/smoke/latest/checks/01-health.md
```

CLI flags:

| Flag | Default | Description |
|---|---|---|
| `--out-dir <path>` | `out/smoke/` | Directory for file reports |
| `--no-file-report` | false | Console output only, no files |
| `--report-only-fail` | false | Only write check files for FAILed checks |

Examples:
```bash
# Console-only (no files, fast CI)
./bin/risk-smoke --headless --no-file-report

# Full report
./bin/risk-smoke --headless

# Only write files for failing checks
./bin/risk-smoke --headless --report-only-fail --out-dir /tmp/smoke-results/
```

---

## Cucumber bare check

Runs the Maven Cucumber-JVM ATDD suite for the **bare-javac** `java-risk-engine` module
(no HTTP server needed — pure in-process logic). Invokes:

```bash
mvn -pl tests/risk-engine-atdd test -q -DskipFailureExit=true
```

then parses `tests/risk-engine-atdd/target/cucumber.json` and reports per-feature
scenario counts (passed / failed / skipped).

### Why is it opt-in?

Maven has a cold-start time of ~60 s on the first run (dependency download). Including
it in the default smoke set would make `risk-smoke --headless` slow for every CI job
that only needs the HTTP/Kafka checks. The Cucumber suite is therefore **disabled by
default** and must be explicitly enabled.

### How to activate

```bash
# Run only the Cucumber check
./bin/risk-smoke --only cucumber-bare --headless

# Include Cucumber in a broader run
./bin/risk-smoke --only health,cucumber-bare --headless

# Auto-include in every run (e.g. in a dedicated CI stage)
RISK_SMOKE_INCLUDE_MAVEN=1 ./bin/risk-smoke --headless
```

### Preconditions

| Precondition | Behaviour if missing |
|---|---|
| `mvn` in `$PATH` | SKIP with message "maven not in PATH" |
| `tests/risk-engine-atdd/pom.xml` exists | SKIP with path hint |
| `target/cucumber.json` generated after run | FAIL with message |

---

## Environment variables

| Variable                      | Default                    |
|-------------------------------|----------------------------|
| `RISK_SMOKE_CONTROLLER_URL`   | `http://localhost:8080`    |
| `RISK_SMOKE_KAFKA_BROKER`     | `localhost:19092`          |
| `RISK_SMOKE_OPENOBSERVE_URL`  | `http://localhost:5080`    |
| `RISK_SMOKE_KAFKA_TOPIC`      | `risk-decisions`           |
| `RISK_SMOKE_INCLUDE_MAVEN`    | _(unset)_ — set to `1` to auto-include `cucumber-bare` |
