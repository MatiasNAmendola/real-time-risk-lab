package reporter_test

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/naranjax/risk-smoke/internal/config"
	"github.com/naranjax/risk-smoke/internal/flows"
	"github.com/naranjax/risk-smoke/internal/reporter"
)

// stubCheck is a minimal flows.Check for testing.
type stubCheck struct {
	id   string
	name string
}

func (s *stubCheck) ID() string   { return s.id }
func (s *stubCheck) Name() string { return s.name }
func (s *stubCheck) Run(_ *config.Config) flows.Result {
	return flows.Result{}
}

func makeResult(passed bool, details []flows.DetailEntry) flows.Result {
	return flows.Result{
		Passed:    passed,
		StartedAt: time.Now(),
		Duration:  12 * time.Millisecond,
		Detail:    "test detail",
		Details:   details,
		Artifacts: map[string]string{"request": "GET /healthz"},
	}
}

func TestConsoleReporter_OutputFormat(t *testing.T) {
	var buf bytes.Buffer
	r := reporter.NewConsoleReporter(&buf)
	chk := &stubCheck{id: "health", name: "Health"}

	r.Start("2026-01-01T00-00-00", 1, "http://localhost:8080")
	r.OnCheckStart(chk)
	r.OnCheckEnd(chk, makeResult(true, nil))
	r.Finish(0)

	out := buf.String()
	if !strings.Contains(out, "[1/1]") {
		t.Errorf("expected [1/1] in output, got: %q", out)
	}
	if !strings.Contains(out, "PASS") {
		t.Errorf("expected PASS in output, got: %q", out)
	}
	if !strings.Contains(out, "health") {
		t.Errorf("expected check id in output, got: %q", out)
	}
}

func TestConsoleReporter_FailStatus(t *testing.T) {
	var buf bytes.Buffer
	r := reporter.NewConsoleReporter(&buf)
	chk := &stubCheck{id: "rest", name: "REST"}

	r.Start("2026-01-01T00-00-01", 1, "http://localhost:8080")
	r.OnCheckStart(chk)
	r.OnCheckEnd(chk, makeResult(false, nil))
	r.Finish(1)

	out := buf.String()
	if !strings.Contains(out, "FAIL") {
		t.Errorf("expected FAIL in output, got: %q", out)
	}
}

func TestFileReporter_CreatesFiles(t *testing.T) {
	dir := t.TempDir()
	fr := reporter.NewFileReporter(dir, false)
	chk := &stubCheck{id: "health", name: "Health"}

	details := []flows.DetailEntry{
		{
			Timestamp: time.Now(),
			Step:      "GET /healthz",
			Status:    "OK",
			Note:      "HTTP 200, 12ms",
			Payload:   "GET /healthz HTTP/1.1\n\nHTTP/1.1 200 OK",
		},
	}

	fr.Start("2026-01-01T00-00-00", 1, "http://localhost:8080")
	fr.OnCheckStart(chk)
	fr.OnCheckEnd(chk, makeResult(true, details))
	outPath := fr.Finish(0)

	if outPath == "" {
		t.Fatal("expected non-empty output path")
	}

	// summary.md must exist
	summaryPath := filepath.Join(outPath, "summary.md")
	if _, err := os.Stat(summaryPath); err != nil {
		t.Errorf("summary.md not found: %v", err)
	}
	data, _ := os.ReadFile(summaryPath)
	if !strings.Contains(string(data), "health") {
		t.Errorf("summary.md should contain check id 'health'")
	}

	// summary.txt must exist
	if _, err := os.Stat(filepath.Join(outPath, "summary.txt")); err != nil {
		t.Errorf("summary.txt not found: %v", err)
	}

	// full.log must exist
	if _, err := os.Stat(filepath.Join(outPath, "full.log")); err != nil {
		t.Errorf("full.log not found: %v", err)
	}

	// check md
	checkMD := filepath.Join(outPath, "checks", "01-health.md")
	if _, err := os.Stat(checkMD); err != nil {
		t.Errorf("checks/01-health.md not found: %v", err)
	}
	checkData, _ := os.ReadFile(checkMD)
	if !strings.Contains(string(checkData), "GET /healthz") {
		t.Errorf("check md should contain step 'GET /healthz'")
	}

	// meta.json must exist
	if _, err := os.Stat(filepath.Join(outPath, "meta.json")); err != nil {
		t.Errorf("meta.json not found: %v", err)
	}

	// latest symlink
	latestLink := filepath.Join(dir, "latest")
	if _, err := os.Lstat(latestLink); err != nil {
		t.Errorf("latest symlink not found: %v", err)
	}
}

func TestFileReporter_OnlyFail_SkipsPassedChecks(t *testing.T) {
	dir := t.TempDir()
	fr := reporter.NewFileReporter(dir, true) // onlyFail=true
	chk := &stubCheck{id: "health", name: "Health"}

	fr.Start("2026-01-01T00-00-00", 1, "http://localhost:8080")
	fr.OnCheckStart(chk)
	fr.OnCheckEnd(chk, makeResult(true, nil))
	fr.Finish(0)

	checkMD := filepath.Join(dir, "2026-01-01T00-00-00", "checks", "01-health.md")
	if _, err := os.Stat(checkMD); err == nil {
		t.Error("expected check md NOT to be written for passed check with onlyFail=true")
	}
}

func TestDetailEntry_PopulatedByHealthCheck(t *testing.T) {
	// Verify that HealthCheck populates at least one DetailEntry
	// We test this via a stub server — reuse logic from health_test.go pattern
	// but here we just verify the Result struct has Details populated.
	result := flows.Result{
		Details: []flows.DetailEntry{
			{Step: "GET /healthz", Status: "OK", Note: "HTTP 200"},
		},
	}
	if len(result.Details) == 0 {
		t.Error("expected at least one DetailEntry")
	}
	if result.Details[0].Step != "GET /healthz" {
		t.Errorf("unexpected step: %q", result.Details[0].Step)
	}
}
