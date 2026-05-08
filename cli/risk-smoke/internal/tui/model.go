package tui

import (
	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbletea"
	"github.com/riskplatform/risk-smoke/internal/config"
	"github.com/riskplatform/risk-smoke/internal/flows"
)

// CheckEntry holds the mutable state for a single check displayed in the TUI.
type CheckEntry struct {
	ID       string
	Name     string
	Status   CheckStatus
	Detail   string
	Request  string
	Response string
	ErrMsg   string
	Latency  string
	Tab      int // 0=detail 1=request 2=response/error
}

// Model is the root Bubble Tea model.
type Model struct {
	cfg      *config.Config
	checks   []*CheckEntry
	cursor   int
	spinner  spinner.Model
	width    int
	height   int
	quitting bool
}

// New builds the initial Model from a config.
func New(cfg *config.Config) Model {
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = StyleStatusRunning

	entries := buildChecks(cfg)

	return Model{
		cfg:     cfg,
		checks:  entries,
		spinner: s,
	}
}

// buildChecks creates the ordered list of check entries.
func buildChecks(cfg *config.Config) []*CheckEntry {
	all := []struct{ id, name string }{
		{flows.CheckHealth, "Health — GET /healthz"},
		{flows.CheckOpenAPI, "OpenAPI — validate /openapi.json"},
		{flows.CheckAsyncAPI, "AsyncAPI — validate /asyncapi.json"},
		{flows.CheckREST, "REST sync x5 — POST /risk"},
		{flows.CheckSSE, "SSE stream — /risk/stream"},
		{flows.CheckWebSocket, "WebSocket bidi — /ws/risk"},
		{flows.CheckWebhook, "Webhook — register + callback"},
		{flows.CheckKafka, "Kafka — topic risk-decisions"},
		{flows.CheckOTEL, "OTEL trace — traceresponse header"},
	}

	entries := make([]*CheckEntry, 0, len(all))
	for _, a := range all {
		entries = append(entries, &CheckEntry{
			ID:     a.id,
			Name:   a.name,
			Status: StatusPending,
		})
	}
	return entries
}

// Init satisfies bubbletea.Model.
func (m Model) Init() tea.Cmd {
	return m.spinner.Tick
}
