package flows

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/naranjax/risk-smoke/internal/config"
)

// CucumberCheck executes `mvn -pl tests/risk-engine-atdd test -q` and parses
// target/cucumber.json. It reports quantity of scenarios pass/fail/skip and
// global status. This check is opt-in (slow, ~60s first run).
type CucumberCheck struct {
	// ProjectRoot is the path to the monorepo root. If empty it is auto-detected
	// via git rev-parse or by climbing directories until pom.xml is found.
	ProjectRoot string
}

func (c *CucumberCheck) ID() string   { return CheckCucumberBare }
func (c *CucumberCheck) Name() string { return "Cucumber bare — ATDD (Maven)" }

// cucumberFeature mirrors the minimal structure of a cucumber.json entry.
type cucumberFeature struct {
	Name     string            `json:"name"`
	Elements []cucumberElement `json:"elements"`
}

type cucumberElement struct {
	Type  string       `json:"type"`
	Steps []cucumberStep `json:"steps"`
}

type cucumberStep struct {
	Result cucumberStepResult `json:"result"`
}

type cucumberStepResult struct {
	Status string `json:"status"`
}

func (c *CucumberCheck) Run(cfg *config.Config) Result {
	startedAt := time.Now()

	// --- 1. Maven available? ---
	if _, err := exec.LookPath("mvn"); err != nil {
		return skipResult(CheckCucumberBare, startedAt, "maven not in PATH")
	}

	// --- 2. Resolve project root ---
	root := c.ProjectRoot
	if root == "" {
		root = detectProjectRoot()
	}
	if root == "" {
		return skipResult(CheckCucumberBare, startedAt, "could not detect monorepo root")
	}

	atddPom := filepath.Join(root, "tests", "risk-engine-atdd", "pom.xml")
	if _, err := os.Stat(atddPom); os.IsNotExist(err) {
		return skipResult(CheckCucumberBare, startedAt,
			fmt.Sprintf("tests/risk-engine-atdd/pom.xml not found (looked at %s)", atddPom))
	}

	// --- 3. Run Maven ---
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	//nolint:gosec
	cmd := exec.CommandContext(ctx, "mvn",
		"-pl", "tests/risk-engine-atdd",
		"test", "-q",
		"-DskipFailureExit=true",
	)
	cmd.Dir = root

	mvnOut, _ := cmd.CombinedOutput()
	mvnExitCode := 0
	if cmd.ProcessState != nil {
		mvnExitCode = cmd.ProcessState.ExitCode()
	}

	artifacts := map[string]string{
		"mvn_output": string(mvnOut),
	}
	if mvnExitCode != 0 && len(mvnOut) == 0 {
		artifacts["mvn_stderr"] = fmt.Sprintf("exit code %d (no output)", mvnExitCode)
	}

	// --- 4. Parse cucumber.json ---
	reportPath := filepath.Join(root, "tests", "risk-engine-atdd", "target", "cucumber.json")
	jsonBytes, err := os.ReadFile(reportPath)
	if err != nil {
		dur := time.Since(startedAt)
		return Result{
			ID:        CheckCucumberBare,
			Passed:    false,
			StartedAt: startedAt,
			Duration:  dur,
			ErrMsg:    fmt.Sprintf("cucumber.json not found after mvn run: %v", err),
			Detail:    "FAILED — cucumber.json not found after mvn run",
			Artifacts: artifacts,
			Details: []DetailEntry{
				{Timestamp: startedAt, Step: "mvn test", Status: "FAIL",
					Note: fmt.Sprintf("exit %d, report missing", mvnExitCode)},
			},
		}
	}
	artifacts["cucumber_json"] = string(jsonBytes)

	// --- 5. Count scenarios ---
	var features []cucumberFeature
	if jsonErr := json.Unmarshal(jsonBytes, &features); jsonErr != nil {
		dur := time.Since(startedAt)
		return Result{
			ID:        CheckCucumberBare,
			Passed:    false,
			StartedAt: startedAt,
			Duration:  dur,
			ErrMsg:    fmt.Sprintf("failed to parse cucumber.json: %v", jsonErr),
			Detail:    "FAILED — malformed cucumber.json",
			Artifacts: artifacts,
		}
	}

	passed, failed, skipped := 0, 0, 0
	var details []DetailEntry

	for _, feat := range features {
		scenarioPassed, scenarioFailed, scenarioSkipped := 0, 0, 0
		for _, el := range feat.Elements {
			if el.Type != "scenario" && el.Type != "scenario_outline" {
				continue
			}
			scenarioStatus := scenarioResult(el.Steps)
			switch scenarioStatus {
			case "passed":
				scenarioPassed++
				passed++
			case "failed":
				scenarioFailed++
				failed++
			default:
				scenarioSkipped++
				skipped++
			}
		}
		total := scenarioPassed + scenarioFailed + scenarioSkipped
		status := "OK"
		if scenarioFailed > 0 {
			status = "FAIL"
		} else if scenarioPassed == 0 && total > 0 {
			status = "SKIP"
		}
		details = append(details, DetailEntry{
			Timestamp: startedAt,
			Step:      fmt.Sprintf("feature: %s", feat.Name),
			Status:    status,
			Note: fmt.Sprintf("%d pass, %d fail, %d skip",
				scenarioPassed, scenarioFailed, scenarioSkipped),
		})
	}

	dur := time.Since(startedAt)

	// --- 6. Determine status ---
	if passed == 0 && failed == 0 && skipped >= 0 {
		// All skipped or empty report
		if passed == 0 && failed == 0 {
			return Result{
				ID:        CheckCucumberBare,
				Passed:    false,
				Skipped:   skipped > 0,
				StartedAt: startedAt,
				Duration:  dur,
				Detail:    fmt.Sprintf("SKIP — no scenarios ran (%d skipped)", skipped),
				Details:   details,
				Artifacts: artifacts,
			}
		}
	}

	if failed >= 1 || passed == 0 {
		return Result{
			ID:        CheckCucumberBare,
			Passed:    false,
			StartedAt: startedAt,
			Duration:  dur,
			ErrMsg:    fmt.Sprintf("%d scenario(s) failed", failed),
			Detail: fmt.Sprintf("FAILED — %d passed, %d failed, %d skipped",
				passed, failed, skipped),
			Details:   details,
			Artifacts: artifacts,
		}
	}

	return Result{
		ID:        CheckCucumberBare,
		Passed:    true,
		StartedAt: startedAt,
		Duration:  dur,
		Detail: fmt.Sprintf("PASSED — %d scenarios passed, %d skipped",
			passed, skipped),
		Details:   details,
		Artifacts: artifacts,
	}
}

// scenarioResult returns "passed", "failed", or "skipped" for a scenario element
// based on its steps.
func scenarioResult(steps []cucumberStep) string {
	for _, s := range steps {
		switch s.Result.Status {
		case "failed":
			return "failed"
		case "undefined", "pending":
			return "skipped"
		}
	}
	// All steps passed (or no steps)
	if len(steps) == 0 {
		return "skipped"
	}
	return "passed"
}

// skipResult is a convenience builder for SKIP results.
func skipResult(id string, startedAt time.Time, reason string) Result {
	return Result{
		ID:        id,
		Passed:    false,
		Skipped:   true,
		StartedAt: startedAt,
		Duration:  time.Since(startedAt),
		Detail:    fmt.Sprintf("SKIP — %s", reason),
		ErrMsg:    reason,
		Details: []DetailEntry{
			{Timestamp: startedAt, Step: "precondition", Status: "SKIP", Note: reason},
		},
	}
}

// detectProjectRoot tries to find the monorepo root by:
//  1. git rev-parse --show-toplevel (fast, reliable inside a repo)
//  2. Walking up from cwd looking for a pom.xml or settings.gradle.kts
func detectProjectRoot() string {
	// Try git first
	out, err := exec.Command("git", "rev-parse", "--show-toplevel").Output()
	if err == nil {
		root := strings.TrimSpace(string(out))
		if root != "" {
			return root
		}
	}

	// Walk up from cwd
	cwd, err := os.Getwd()
	if err != nil {
		return ""
	}
	dir := cwd
	for {
		if _, e := os.Stat(filepath.Join(dir, "settings.gradle.kts")); e == nil {
			return dir
		}
		if _, e := os.Stat(filepath.Join(dir, "pom.xml")); e == nil {
			return dir
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}
	return ""
}
