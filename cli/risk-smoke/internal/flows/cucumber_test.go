package flows_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/naranjax/risk-smoke/internal/config"
	"github.com/naranjax/risk-smoke/internal/flows"
)

// writeCucumberJSON writes a synthetic cucumber.json under
// <root>/tests/risk-engine-atdd/target/cucumber.json so the check can parse it
// without actually running Maven.
func writeCucumberJSON(t *testing.T, root string, features []any) {
	t.Helper()
	dir := filepath.Join(root, "tests", "risk-engine-atdd", "target")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	b, err := json.Marshal(features)
	if err != nil {
		t.Fatalf("json.Marshal: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "cucumber.json"), b, 0o644); err != nil {
		t.Fatalf("WriteFile: %v", err)
	}
}

// setupFakeProject creates a minimal monorepo skeleton and returns its root path.
func setupFakeProject(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	pomDir := filepath.Join(root, "tests", "risk-engine-atdd")
	if err := os.MkdirAll(pomDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	// pom.xml must exist for the precondition check
	if err := os.WriteFile(filepath.Join(pomDir, "pom.xml"), []byte("<project/>"), 0o644); err != nil {
		t.Fatalf("WriteFile pom.xml: %v", err)
	}
	return root
}

// makeFakeFeature returns a single-feature cucumber JSON structure.
func makeFakeFeature(name string, scenarios []map[string]any) map[string]any {
	return map[string]any{
		"name":     name,
		"elements": scenarios,
	}
}

func passedScenario() map[string]any {
	return map[string]any{
		"type": "scenario",
		"steps": []map[string]any{
			{"result": map[string]any{"status": "passed"}},
			{"result": map[string]any{"status": "passed"}},
		},
	}
}

func failedScenario() map[string]any {
	return map[string]any{
		"type": "scenario",
		"steps": []map[string]any{
			{"result": map[string]any{"status": "passed"}},
			{"result": map[string]any{"status": "failed"}},
		},
	}
}

func skippedScenario() map[string]any {
	return map[string]any{
		"type": "scenario",
		"steps": []map[string]any{
			{"result": map[string]any{"status": "undefined"}},
		},
	}
}

// runCheckWithFakeReport builds a CucumberCheck pointing at the fake root,
// but bypasses the Maven invocation by writing target/cucumber.json directly
// and injecting a noop mvn via PATH manipulation.
//
// We set ProjectRoot so detectProjectRoot() is bypassed.
// Maven is still invoked — so we skip the test when mvn is NOT in PATH; otherwise
// we let it fail (mvn -pl ... will fail because there is no real Maven project).
// The key behaviour we test is the JSON-parsing logic, so we write the JSON *before*
// the check runs (the check will overwrite it only if mvn succeeds; but since
// -DskipFailureExit=true is passed and we write the file beforehand, the report
// will still be there when mvn exits with a non-zero code).
//
// To make the test truly hermetic (no mvn dependency), we use a tiny shell stub
// as a fake mvn that just exits 0.
func runCheckWithFakeMvn(t *testing.T, root string) *flows.CucumberCheck {
	t.Helper()

	// Create a tiny fake "mvn" script in a temp bin dir
	binDir := t.TempDir()
	fakeMvn := filepath.Join(binDir, "mvn")
	script := "#!/bin/sh\nexit 0\n"
	if err := os.WriteFile(fakeMvn, []byte(script), 0o755); err != nil {
		t.Fatalf("write fake mvn: %v", err)
	}

	// Prepend fake bin dir to PATH so exec.LookPath finds it
	origPath := os.Getenv("PATH")
	t.Setenv("PATH", binDir+string(os.PathListSeparator)+origPath)

	return &flows.CucumberCheck{ProjectRoot: root}
}

// -----------------------------------------------------------------------

func TestCucumberCheck_AllPassed(t *testing.T) {
	root := setupFakeProject(t)
	features := []any{
		makeFakeFeature("Risk decision scenarios", []map[string]any{
			passedScenario(),
			passedScenario(),
		}),
	}
	writeCucumberJSON(t, root, features)

	chk := runCheckWithFakeMvn(t, root)
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Passed {
		t.Errorf("expected PASSED, got: %s — %s", result.Detail, result.ErrMsg)
	}
	if result.Skipped {
		t.Error("expected Skipped=false")
	}
	if result.Duration < 0 {
		t.Error("duration should be non-negative")
	}
}

func TestCucumberCheck_SomeFailed(t *testing.T) {
	root := setupFakeProject(t)
	features := []any{
		makeFakeFeature("Idempotency", []map[string]any{
			passedScenario(),
			failedScenario(),
		}),
	}
	writeCucumberJSON(t, root, features)

	chk := runCheckWithFakeMvn(t, root)
	cfg := config.Default()

	result := chk.Run(cfg)

	if result.Passed {
		t.Error("expected FAILED when there is at least one failed scenario")
	}
}

func TestCucumberCheck_AllSkipped(t *testing.T) {
	root := setupFakeProject(t)
	features := []any{
		makeFakeFeature("Pending feature", []map[string]any{
			skippedScenario(),
		}),
	}
	writeCucumberJSON(t, root, features)

	chk := runCheckWithFakeMvn(t, root)
	cfg := config.Default()

	result := chk.Run(cfg)

	if result.Passed {
		t.Error("expected FAILED/SKIPPED when all scenarios are skipped")
	}
}

func TestCucumberCheck_MultiFeature(t *testing.T) {
	root := setupFakeProject(t)
	features := []any{
		makeFakeFeature("Feature A", []map[string]any{
			passedScenario(),
			passedScenario(),
		}),
		makeFakeFeature("Feature B", []map[string]any{
			passedScenario(),
			skippedScenario(),
		}),
	}
	writeCucumberJSON(t, root, features)

	chk := runCheckWithFakeMvn(t, root)
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Passed {
		t.Errorf("expected PASSED for multi-feature with only passes, got: %s", result.Detail)
	}
	// Should have one DetailEntry per feature
	if len(result.Details) != 2 {
		t.Errorf("expected 2 detail entries (one per feature), got %d", len(result.Details))
	}
}

func TestCucumberCheck_MissingPom_Skips(t *testing.T) {
	root := t.TempDir() // no pom.xml inside tests/risk-engine-atdd

	chk := &flows.CucumberCheck{ProjectRoot: root}
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Skipped {
		t.Errorf("expected SKIP when pom.xml is missing, got: %s", result.Detail)
	}
}

func TestCucumberCheck_SkipResult_Fields(t *testing.T) {
	root := t.TempDir()
	chk := &flows.CucumberCheck{ProjectRoot: root}
	cfg := config.Default()

	before := time.Now()
	result := chk.Run(cfg)
	after := time.Now()

	if !result.Skipped {
		t.Skip("expected a skip result (no pom.xml), cannot test fields otherwise")
	}
	if result.StartedAt.Before(before) || result.StartedAt.After(after) {
		t.Errorf("StartedAt %v out of range [%v, %v]", result.StartedAt, before, after)
	}
	if result.Duration < 0 {
		t.Error("Duration should not be negative")
	}
}

func TestCucumberCheck_MavenNotInPath_Skips(t *testing.T) {
	root := setupFakeProject(t)
	// Override PATH to a directory with no mvn
	emptyBin := t.TempDir()
	t.Setenv("PATH", emptyBin)

	chk := &flows.CucumberCheck{ProjectRoot: root}
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Skipped {
		t.Errorf("expected SKIP when mvn is not in PATH, got: %s", result.Detail)
	}
	if result.ErrMsg != "maven not in PATH" {
		t.Errorf("unexpected ErrMsg: %q", result.ErrMsg)
	}
}
