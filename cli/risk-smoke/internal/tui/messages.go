package tui

// CheckStatus represents the execution state of a single check.
type CheckStatus int

const (
	StatusPending CheckStatus = iota
	StatusRunning
	StatusPassed
	StatusFailed
	StatusSkipped
)

func (s CheckStatus) Icon() string {
	switch s {
	case StatusPending:
		return "⏳"
	case StatusRunning:
		return "🟡"
	case StatusPassed:
		return "✅"
	case StatusFailed:
		return "❌"
	case StatusSkipped:
		return "⏭️"
	default:
		return "?"
	}
}

func (s CheckStatus) String() string {
	switch s {
	case StatusPending:
		return "pending"
	case StatusRunning:
		return "running"
	case StatusPassed:
		return "passed"
	case StatusFailed:
		return "failed"
	case StatusSkipped:
		return "skipped"
	default:
		return "unknown"
	}
}

// CheckResult is the tea.Msg emitted when a check finishes.
type CheckResult struct {
	ID      string
	Status  CheckStatus
	Detail  string // human-readable outcome
	Request string // raw request info
	Response string // raw response info
	ErrMsg  string // non-empty on failure
	Latency string // optional latency string (e.g. "p50=12ms p99=45ms")
}

// RunAllMsg triggers all checks to execute.
type RunAllMsg struct{}

// RunCheckMsg triggers a single check by ID.
type RunCheckMsg struct {
	ID string
}

// SkipCheckMsg marks a check as skipped.
type SkipCheckMsg struct {
	ID string
}

// TabMsg cycles the detail tab on the selected check.
type TabMsg struct{}
