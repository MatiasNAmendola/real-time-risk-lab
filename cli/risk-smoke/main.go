package main

import (
	"flag"
	"fmt"
	"os"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/riskplatform/risk-smoke/internal/config"
	"github.com/riskplatform/risk-smoke/internal/flows"
	"github.com/riskplatform/risk-smoke/internal/reporter"
	"github.com/riskplatform/risk-smoke/internal/tui"
)

func main() {
	cfg := config.Default()

	var (
		headless       = flag.Bool("headless", false, "Run all checks without TUI, exit 0/1")
		ci             = flag.Bool("ci", false, "Alias for --headless")
		baseURL        = flag.String("base-url", "", "Override controller base URL (default: $RISK_SMOKE_CONTROLLER_URL or http://localhost:8080)")
		kafkaBrk       = flag.String("kafka-broker", "", "Kafka/Redpanda broker address (default: localhost:19092)")
		openobs        = flag.String("openobserve", "", "OpenObserve base URL (default: http://localhost:5080)")
		onlyChecks     = flag.String("only", "", "Comma-separated list of check IDs to run (e.g. health,rest,kafka)")
		outDir         = flag.String("out-dir", "out/smoke/", "Directory for file reports")
		noFileReport   = flag.Bool("no-file-report", false, "Disable file output (console only)")
		reportOnlyFail = flag.Bool("report-only-fail", false, "Only write file report for failed checks")
	)

	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, `risk-smoke — Risk Decision Platform Risk Engine smoke runner

USAGE
  risk-smoke [flags]

FLAGS
`)
		flag.PrintDefaults()
		fmt.Fprintf(os.Stderr, `
CHECK IDs (for --only)
  health          GET /healthz
  openapi         GET /openapi.json (validates webhooks key)
  asyncapi        GET /asyncapi.json (validates version 3.x)
  rest            POST /risk x5 with varied amounts
  sse             GET /risk/stream (SSE, up to 3 events)
  websocket       WS /ws/risk (bidi, 3 messages)
  webhook         Register local listener, fire DECLINE tx, wait callback
  kafka           Consume topic risk-decisions (Redpanda)
  otel            POST /risk → traceresponse → OpenObserve trace lookup
  cucumber-bare   Run Gradle Cucumber ATDD for java-risk-engine (opt-in, slow)

EXAMPLES
  risk-smoke                                        # interactive TUI
  risk-smoke --headless                             # CI mode, exit 0/1
  risk-smoke --only health,rest,kafka               # run subset
  risk-smoke --only cucumber-bare --headless        # run Cucumber ATDD only
  risk-smoke --base-url http://staging:8080         # custom URL
  risk-smoke --ci --only health                     # fastest CI health gate

ENV VARS
  RISK_SMOKE_CONTROLLER_URL   (default: http://localhost:8080)
  RISK_SMOKE_KAFKA_BROKER     (default: localhost:19092)
  RISK_SMOKE_OPENOBSERVE_URL  (default: http://localhost:5080)
  RISK_SMOKE_KAFKA_TOPIC      (default: risk-decisions)
  RISK_SMOKE_INCLUDE_ATDD    set to 1 to auto-include cucumber-bare in every run
`)
	}
	flag.Parse()

	if *baseURL != "" {
		cfg.BaseURL = *baseURL
		cfg.ControllerURL = *baseURL
	}
	if *kafkaBrk != "" {
		cfg.KafkaBroker = *kafkaBrk
	}
	if *openobs != "" {
		cfg.OpenObserveURL = *openobs
	}
	if *onlyChecks != "" {
		cfg.OnlyChecks = splitComma(*onlyChecks)
	}
	cfg.Headless = *headless || *ci
	cfg.OutDir = *outDir
	cfg.NoFileReport = *noFileReport
	cfg.ReportOnlyFail = *reportOnlyFail

	if cfg.Headless {
		os.Exit(runHeadless(cfg))
	}

	m := tui.New(cfg)
	p := tea.NewProgram(m, tea.WithAltScreen())
	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "TUI error: %v\n", err)
		os.Exit(1)
	}
}

func runHeadless(cfg *config.Config) int {
	allChecks := flows.All(cfg)
	selected := filterChecks(allChecks, cfg.OnlyChecks)

	runID := time.Now().UTC().Format("2006-01-02T15-04-05")

	// Build reporter
	consoleRep := reporter.NewConsoleReporter(os.Stdout)
	var rep reporter.Reporter
	if cfg.NoFileReport {
		rep = consoleRep
	} else {
		fileRep := reporter.NewFileReporter(cfg.OutDir, cfg.ReportOnlyFail)
		rep = reporter.NewCompositeReporter(consoleRep, fileRep)
	}

	rep.Start(runID, len(selected), cfg.BaseURL)

	exitCode := 0
	for _, chk := range selected {
		rep.OnCheckStart(chk)
		result := chk.Run(cfg)
		rep.OnCheckEnd(chk, result)
		if !result.Passed && !result.Skipped {
			exitCode = 1
		}
	}

	outPath := rep.Finish(exitCode)
	if outPath != "" {
		fmt.Printf("\nReport: %s\n", outPath)
		fmt.Printf("        cat %s/summary.md\n", outPath)
	}
	return exitCode
}

func filterChecks(all []flows.Check, only []string) []flows.Check {
	if len(only) == 0 {
		return all
	}
	set := make(map[string]bool, len(only))
	for _, id := range only {
		set[strings.TrimSpace(id)] = true
	}
	var out []flows.Check
	for _, c := range all {
		if set[c.ID()] {
			out = append(out, c)
		}
	}
	return out
}

func splitComma(s string) []string {
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if t := strings.TrimSpace(p); t != "" {
			out = append(out, t)
		}
	}
	return out
}
