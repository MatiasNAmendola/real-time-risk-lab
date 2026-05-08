// Package flows defines the Check interface and check IDs used across the TUI and headless runner.
package flows

import (
	"os"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

// Check IDs — used as map keys and --only filter values.
const (
	CheckHealth       = "health"
	CheckOpenAPI      = "openapi"
	CheckAsyncAPI     = "asyncapi"
	CheckREST         = "rest"
	CheckSSE          = "sse"
	CheckWebSocket    = "websocket"
	CheckWebhook      = "webhook"
	CheckKafka        = "kafka"
	CheckOTEL         = "otel"
	CheckCucumberBare = "cucumber-bare"
)

// DetailEntry captures a single step/event within a check execution.
type DetailEntry struct {
	Timestamp time.Time
	Step      string // e.g. "POST /risk", "open WS", "wait Kafka msg"
	Status    string // OK / FAIL / INFO
	Note      string // brief human-readable note
	Payload   string // raw request/response body — written to file only
}

// Result is the outcome of executing a single check.
type Result struct {
	ID        string
	Passed    bool
	Skipped   bool
	Detail    string // 1-line summary for console
	Request   string // raw request info (legacy; kept for TUI)
	Response  string // raw response info (legacy; kept for TUI)
	ErrMsg    string
	Latency   string
	Duration  time.Duration     // precise duration
	StartedAt time.Time         // when the check started
	Details   []DetailEntry     // step-level events
	Artifacts map[string]string // named blobs: "request_body", "response_body", "headers", …
	Error     error
}

// Check is the interface every smoke check must implement.
type Check interface {
	ID() string
	Name() string
	Run(cfg *config.Config) Result
}

// All returns the full ordered list of checks.
// cucumber-bare is opt-in: it is included only when explicitly requested via
// --only cucumber-bare (or any --only list that contains it) OR when the
// environment variable RISK_SMOKE_INCLUDE_ATDD=1 is set.
// This avoids a 60-90 s Gradle cold-start in the default smoke run.
func All(cfg *config.Config) []Check {
	base := []Check{
		&HealthCheck{},
		&OpenAPICheck{},
		&AsyncAPICheck{},
		&RESTCheck{},
		&SSECheck{},
		&WebSocketCheck{},
		&WebhookCheck{},
		&KafkaCheck{},
		&OTELCheck{},
	}

	if cucumberEnabled(cfg) {
		base = append(base, &CucumberCheck{})
	}
	return base
}

// cucumberEnabled returns true when the cucumber-bare check should be included
// in the default All() list.
func cucumberEnabled(cfg *config.Config) bool {
	if os.Getenv("RISK_SMOKE_INCLUDE_ATDD") == "1" {
		return true
	}
	for _, id := range cfg.OnlyChecks {
		if id == CheckCucumberBare {
			return true
		}
	}
	return false
}
