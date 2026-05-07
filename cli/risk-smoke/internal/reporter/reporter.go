// Package reporter provides structured reporting for smoke-check runs.
package reporter

import "github.com/naranjax/risk-smoke/internal/flows"

// Reporter is the interface for observing a smoke run and producing output.
type Reporter interface {
	// Start is called once at the beginning of a run.
	Start(runID string, total int, baseURL string)

	// OnCheckStart is called just before a check begins execution.
	OnCheckStart(c flows.Check)

	// OnCheckProgress is called for intermediate notes during execution
	// (shown in console if TUI mode, always written to file).
	OnCheckProgress(c flows.Check, msg string)

	// OnCheckEnd is called when a check finishes.
	OnCheckEnd(c flows.Check, result flows.Result)

	// Finish is called once all checks are done.
	// It returns the path to the output directory (empty if no file report).
	Finish(exitCode int) string
}
