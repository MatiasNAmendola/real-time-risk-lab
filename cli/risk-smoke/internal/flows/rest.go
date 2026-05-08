package flows

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"time"

	riskclient "github.com/riskplatform/risk-client"
	"github.com/riskplatform/risk-smoke/internal/config"
)

var restAmounts = []int64{1000, 50000, 150000, 200000, 500000}

// RESTCheck posts to /risk 5 times via the risk-client SDK and reports latency statistics.
type RESTCheck struct{}

func (c *RESTCheck) ID() string   { return CheckREST }
func (c *RESTCheck) Name() string { return "REST sync x5 — POST /risk" }

func (c *RESTCheck) Run(cfg *config.Config) Result {
	startedAt := time.Now()

	sdkClient := riskclient.NewWithServerOverride(
		riskclient.Config{
			APIKey:  "smoke-test",
			Timeout: 10 * time.Second,
			Retry:   riskclient.NoRetry(),
		},
		cfg.BaseURL,
		nil,
	)

	var latencies []time.Duration
	var responses []string
	var errs []string
	var details []DetailEntry

	for i, amount := range restAmounts {
		req := riskclient.RiskRequest{
			TransactionID:  fmt.Sprintf("t-smoke-%03d", i+1),
			CorrelationID:  fmt.Sprintf("corr-%03d", i+1),
			CustomerID:     fmt.Sprintf("u-%03d", i+1),
			AmountCents:    amount,
			IdempotencyKey: fmt.Sprintf("idem-smoke-%03d", i+1),
		}

		stepStart := time.Now()
		decision, err := sdkClient.Sync.Evaluate(context.Background(), req)
		elapsed := time.Since(stepStart)

		if err != nil {
			errs = append(errs, fmt.Sprintf("#%d error: %v", i+1, err))
			details = append(details, DetailEntry{
				Timestamp: stepStart,
				Step:      fmt.Sprintf("POST /risk tx=%s amountCents=%d", req.TransactionID, amount),
				Status:    "FAIL",
				Note:      fmt.Sprintf("error: %v", err),
			})
			continue
		}

		latencies = append(latencies, elapsed)
		note := fmt.Sprintf("HTTP 200, %dms, decision=%s", elapsed.Milliseconds(), decision.Decision)
		responses = append(responses, fmt.Sprintf("#%d %.0fms: %s", i+1, float64(elapsed.Milliseconds()), decision.Decision))

		details = append(details, DetailEntry{
			Timestamp: stepStart,
			Step:      fmt.Sprintf("POST /risk tx=%s amountCents=%d", req.TransactionID, amount),
			Status:    "OK",
			Note:      note,
		})
	}

	totalDur := time.Since(startedAt)

	if len(errs) > 0 && len(latencies) == 0 {
		return Result{
			ID:        CheckREST,
			Passed:    false,
			StartedAt: startedAt,
			Duration:  totalDur,
			ErrMsg:    strings.Join(errs, "; "),
			Detail:    fmt.Sprintf("FAILED — all %d requests failed: %s", len(errs), errs[0]),
			Details:   details,
		}
	}

	latStr := latencyStats(latencies)
	detail := fmt.Sprintf("PASSED — %d/5 ok, %s", len(latencies), latStr)
	if len(errs) > 0 {
		detail = fmt.Sprintf("PARTIAL — %d/5 ok, %s, errors: %s", len(latencies), latStr, strings.Join(errs, "; "))
	}

	return Result{
		ID:        CheckREST,
		Passed:    len(latencies) == 5 && len(errs) == 0,
		StartedAt: startedAt,
		Duration:  totalDur,
		Request:   fmt.Sprintf("POST %s/risk x5 via risk-client SDK", cfg.BaseURL),
		Response:  strings.Join(responses, "\n"),
		ErrMsg:    strings.Join(errs, "\n"),
		Detail:    detail,
		Latency:   latStr,
		Details:   details,
	}
}

func latencyStats(d []time.Duration) string {
	if len(d) == 0 {
		return "n/a"
	}
	sorted := make([]time.Duration, len(d))
	copy(sorted, d)
	sort.Slice(sorted, func(i, j int) bool { return sorted[i] < sorted[j] })

	p50 := sorted[len(sorted)/2]
	p99 := sorted[len(sorted)-1]
	return fmt.Sprintf("p50=%dms p99=%dms", p50.Milliseconds(), p99.Milliseconds())
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}
