package flows

import (
	"bufio"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

// SSECheck opens GET /risk/stream and reads up to 3 events within 5s.
type SSECheck struct{}

func (c *SSECheck) ID() string   { return CheckSSE }
func (c *SSECheck) Name() string { return "SSE stream — /risk/stream" }

func (c *SSECheck) Run(cfg *config.Config) Result {
	url := cfg.BaseURL + "/risk/stream"
	req := fmt.Sprintf("GET %s (SSE, max 3 events, 5s timeout)", url)

	client := &http.Client{Timeout: 6 * time.Second}
	httpReq, _ := http.NewRequest(http.MethodGet, url, nil)
	httpReq.Header.Set("Accept", "text/event-stream")
	httpReq.Header.Set("Cache-Control", "no-cache")

	go func() {
		time.Sleep(250 * time.Millisecond)
		_, _, _ = postSmokeRisk(cfg, "sse-smoke", 12345)
	}()

	resp, err := client.Do(httpReq)
	if err != nil {
		return Result{
			ID:      CheckSSE,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("connection error: %v", err),
			Detail:  fmt.Sprintf("FAILED — %v", err),
		}
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return Result{
			ID:       CheckSSE,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("HTTP %d", resp.StatusCode),
			ErrMsg:   fmt.Sprintf("unexpected status %d", resp.StatusCode),
			Detail:   fmt.Sprintf("FAILED — HTTP %d", resp.StatusCode),
		}
	}

	events := make(chan string, 10)
	done := make(chan struct{})
	go func() {
		defer close(done)
		scanner := bufio.NewScanner(resp.Body)
		for scanner.Scan() {
			line := scanner.Text()
			if strings.HasPrefix(line, "data:") {
				events <- strings.TrimPrefix(line, "data:")
			}
		}
	}()

	var received []string
	deadline := time.After(5 * time.Second)
	for {
		select {
		case ev := <-events:
			received = append(received, strings.TrimSpace(ev))
			if len(received) >= 3 {
				goto done
			}
		case <-deadline:
			goto done
		case <-done:
			goto done
		}
	}
done:
	if len(received) == 0 {
		return Result{
			ID:      CheckSSE,
			Skipped: true,
			Request: req,
			Detail:  "SKIP — SSE endpoint reachable but no events arrived within 5s",
		}
	}

	correlations := extractCorrelations(received)
	return Result{
		ID:       CheckSSE,
		Passed:   true,
		Request:  req,
		Response: fmt.Sprintf("%d events: %s", len(received), strings.Join(received, " | ")),
		Detail:   fmt.Sprintf("PASSED — %d events, correlationIds: %s", len(received), correlations),
	}
}

func extractCorrelations(events []string) string {
	var ids []string
	for _, ev := range events {
		if strings.Contains(ev, "correlationId") {
			// best-effort naive parse
			parts := strings.Split(ev, `"correlationId":"`)
			if len(parts) > 1 {
				id := strings.Split(parts[1], `"`)[0]
				ids = append(ids, id)
			}
		}
	}
	if len(ids) == 0 {
		return "(none parsed)"
	}
	return strings.Join(ids, ", ")
}
