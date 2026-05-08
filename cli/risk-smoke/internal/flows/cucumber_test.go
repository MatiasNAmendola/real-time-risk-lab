package flows_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
	"github.com/riskplatform/risk-smoke/internal/flows"
)

// writeCucumberJSON writes a synthetic Cucumber report under
// <root>/tests/risk-engine-atdd/build/cucumber-reports/report.json so the check can parse it
// without actually running Gradle.
func writeCucumberJSON(t *testing.T, root string, features []any) {
	t.Helper()
	dir := filepath.Join(root, "tests", "risk-engine-atdd", "build", "cucumber-reports")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	b, err := json.Marshal(features)
	if err != nil {
		t.Fatalf("json.Marshal: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "report.json"), b, 0o644); err != nil {
		t.Fatalf("WriteFile: %v", err)
	}
}

// setupFakeProject creates a minimal monorepo skeleton and returns its root path.
func setupFakeProject(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	atddDir := filepath.Join(root, "tests", "risk-engine-atdd")
	if err := os.MkdirAll(atddDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(root, "settings.gradle.kts"), []byte("rootProject.name = \"fake\"\n"), 0o644); err != nil {
		t.Fatalf("WriteFile settings.gradle.kts: %v", err)
	}
	gradlew := filepath.Join(root, "gradlew")
	if err := os.WriteFile(gradlew, []byte("#!/bin/sh\nexit 0\n"), 0o755); err != nil {
		t.Fatalf("WriteFile gradlew: %v", err)
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

// runCheckWithFakeGradle builds a CucumberCheck pointing at the fake root.
// setupFakeProject installs a no-op gradlew and tests write the JSON before
// the check runs, so this exercises the parser without invoking real Gradle.
func runCheckWithFakeGradle(t *testing.T, root string) *flows.CucumberCheck {
	t.Helper()
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

	chk := runCheckWithFakeGradle(t, root)
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

	chk := runCheckWithFakeGradle(t, root)
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

	chk := runCheckWithFakeGradle(t, root)
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

	chk := runCheckWithFakeGradle(t, root)
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

func TestCucumberCheck_MissingGradleWrapper_Skips(t *testing.T) {
	root := t.TempDir() // no gradlew at repo root

	chk := &flows.CucumberCheck{ProjectRoot: root}
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Skipped {
		t.Errorf("expected SKIP when gradlew is missing, got: %s", result.Detail)
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
		t.Skip("expected a skip result (no gradlew), cannot test fields otherwise")
	}
	if result.StartedAt.Before(before) || result.StartedAt.After(after) {
		t.Errorf("StartedAt %v out of range [%v, %v]", result.StartedAt, before, after)
	}
	if result.Duration < 0 {
		t.Error("Duration should not be negative")
	}
}

func TestCucumberCheck_MissingGradleWrapperErrMsg(t *testing.T) {
	root := setupFakeProject(t)
	if err := os.Remove(filepath.Join(root, "gradlew")); err != nil {
		t.Fatalf("remove gradlew: %v", err)
	}

	chk := &flows.CucumberCheck{ProjectRoot: root}
	cfg := config.Default()

	result := chk.Run(cfg)

	if !result.Skipped {
		t.Errorf("expected SKIP when gradlew is missing, got: %s", result.Detail)
	}
	if !strings.Contains(result.ErrMsg, "Gradle wrapper not found") {
		t.Errorf("unexpected ErrMsg: %q", result.ErrMsg)
	}
}
