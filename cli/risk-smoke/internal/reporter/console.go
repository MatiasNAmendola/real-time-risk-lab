package reporter

import (
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/naranjax/risk-smoke/internal/flows"
)

// ConsoleReporter writes one-line-per-check output to a writer (usually os.Stdout).
type ConsoleReporter struct {
	w       io.Writer
	total   int
	current int
	startAt time.Time
	results []checkSummary
}

type checkSummary struct {
	idx      int
	id       string
	name     string
	status   string
	duration time.Duration
	message  string
}

// NewConsoleReporter creates a ConsoleReporter writing to w.
func NewConsoleReporter(w io.Writer) *ConsoleReporter {
	return &ConsoleReporter{w: w}
}

func (r *ConsoleReporter) Start(runID string, total int, baseURL string) {
	r.total = total
	r.current = 0
	r.startAt = time.Now()
	fmt.Fprintf(r.w, "risk-smoke — %d check(s) — %s\n\n", total, baseURL)
}

func (r *ConsoleReporter) OnCheckStart(c flows.Check) {
	r.current++
}

func (r *ConsoleReporter) OnCheckProgress(_ flows.Check, _ string) {
	// headless: silence progress; TUI handles this separately
}

func (r *ConsoleReporter) OnCheckEnd(c flows.Check, result flows.Result) {
	var status string
	switch {
	case result.Skipped:
		status = "SKIP"
	case result.Passed:
		status = "PASS"
	default:
		status = "FAIL"
	}

	dur := result.Duration
	if dur == 0 {
		dur = 1 * time.Millisecond
	}

	msg := result.Detail
	if msg == "" {
		msg = result.ErrMsg
	}

	fmt.Fprintf(r.w, "  [%d/%d] %-12s  %-4s  (%dms)\n",
		r.current, r.total, c.ID(), status, dur.Milliseconds())

	r.results = append(r.results, checkSummary{
		idx:      r.current,
		id:       c.ID(),
		name:     c.Name(),
		status:   status,
		duration: dur,
		message:  msg,
	})
}

func (r *ConsoleReporter) Finish(exitCode int) string {
	total := time.Since(r.startAt)
	pass, fail, skip := 0, 0, 0
	for _, s := range r.results {
		switch s.status {
		case "PASS":
			pass++
		case "FAIL":
			fail++
		case "SKIP":
			skip++
		}
	}

	fmt.Fprintf(r.w, "\n")
	fmt.Fprintf(r.w, "%s\n", strings.Repeat("─", 54))
	fmt.Fprintf(r.w, "  Total: %d  PASS: %d  FAIL: %d  SKIP: %d  (%.1fs)\n",
		len(r.results), pass, fail, skip, total.Seconds())
	fmt.Fprintf(r.w, "%s\n", strings.Repeat("─", 54))
	if exitCode == 0 {
		fmt.Fprintln(r.w, "  All checks passed.")
	} else {
		fmt.Fprintln(r.w, "  One or more checks FAILED.")
	}
	return ""
}
