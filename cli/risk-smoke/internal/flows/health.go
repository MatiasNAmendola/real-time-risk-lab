package flows

import (
	"context"
	"fmt"
	"time"

	riskclient "github.com/riskplatform/risk-client"
	"github.com/riskplatform/risk-smoke/internal/config"
)

// HealthCheck hits GET /healthz on the controller via the risk-client SDK.
// A 200 response is treated as healthy regardless of body content.
type HealthCheck struct{}

func (h *HealthCheck) ID() string   { return CheckHealth }
func (h *HealthCheck) Name() string { return "Health — GET /healthz" }

func (h *HealthCheck) Run(cfg *config.Config) Result {
	reqLine := fmt.Sprintf("GET %s/healthz", cfg.BaseURL)
	startedAt := time.Now()

	sdkClient := riskclient.NewWithServerOverride(
		riskclient.Config{
			APIKey:  "smoke-test",
			Timeout: 5 * time.Second,
			Retry:   riskclient.NoRetry(),
		},
		cfg.BaseURL,
		nil,
	)

	stepStart := time.Now()
	status, err := sdkClient.Sync.Health(context.Background())
	elapsed := time.Since(stepStart)
	totalDur := time.Since(startedAt)

	if err != nil {
		return Result{
			ID:        CheckHealth,
			Passed:    false,
			StartedAt: startedAt,
			Duration:  totalDur,
			Request:   reqLine,
			ErrMsg:    fmt.Sprintf("error: %v", err),
			Detail:    fmt.Sprintf("FAILED — %v (%.0fms)", err, float64(elapsed.Milliseconds())),
			Details: []DetailEntry{{
				Timestamp: stepStart,
				Step:      fmt.Sprintf("GET %s/healthz", cfg.BaseURL),
				Status:    "FAIL",
				Note:      fmt.Sprintf("error: %v", err),
			}},
		}
	}

	// A 200 response with no or partial body is treated as UP.
	// Some implementations return plain 200 with empty body.
	statusStr := status.Status
	if statusStr == "" {
		statusStr = "UP"
	}
	respSummary := fmt.Sprintf("HTTP 200 (%.0fms), status=%s", float64(elapsed.Milliseconds()), statusStr)
	latency := fmt.Sprintf("%.0fms", float64(elapsed.Milliseconds()))

	return Result{
		ID:        CheckHealth,
		Passed:    true,
		StartedAt: startedAt,
		Duration:  totalDur,
		Request:   reqLine,
		Response:  respSummary,
		Detail:    fmt.Sprintf("PASSED — %s", respSummary),
		Latency:   latency,
		Details: []DetailEntry{{
			Timestamp: stepStart,
			Step:      fmt.Sprintf("GET %s/healthz", cfg.BaseURL),
			Status:    "OK",
			Note:      fmt.Sprintf("HTTP 200, %.0fms, status=%s", float64(elapsed.Milliseconds()), statusStr),
		}},
	}
}
