package reporter

import "github.com/naranjax/risk-smoke/internal/flows"

// CompositeReporter fans out calls to multiple Reporters.
type CompositeReporter struct {
	reporters []Reporter
}

// NewCompositeReporter creates a Reporter that delegates to all provided reporters.
func NewCompositeReporter(reporters ...Reporter) Reporter {
	return &CompositeReporter{reporters: reporters}
}

func (c *CompositeReporter) Start(runID string, total int, baseURL string) {
	for _, r := range c.reporters {
		r.Start(runID, total, baseURL)
	}
}

func (c *CompositeReporter) OnCheckStart(chk flows.Check) {
	for _, r := range c.reporters {
		r.OnCheckStart(chk)
	}
}

func (c *CompositeReporter) OnCheckProgress(chk flows.Check, msg string) {
	for _, r := range c.reporters {
		r.OnCheckProgress(chk, msg)
	}
}

func (c *CompositeReporter) OnCheckEnd(chk flows.Check, result flows.Result) {
	for _, r := range c.reporters {
		r.OnCheckEnd(chk, result)
	}
}

// Finish calls Finish on all reporters and returns the last non-empty path.
func (c *CompositeReporter) Finish(exitCode int) string {
	var last string
	for _, r := range c.reporters {
		if p := r.Finish(exitCode); p != "" {
			last = p
		}
	}
	return last
}
