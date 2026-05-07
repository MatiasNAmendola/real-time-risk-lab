package flows

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/naranjax/risk-smoke/internal/config"
)

// AsyncAPICheck fetches /asyncapi.json and validates asyncapi version 3.x.
type AsyncAPICheck struct{}

func (c *AsyncAPICheck) ID() string   { return CheckAsyncAPI }
func (c *AsyncAPICheck) Name() string { return "AsyncAPI — validate /asyncapi.json" }

func (c *AsyncAPICheck) Run(cfg *config.Config) Result {
	url := cfg.BaseURL + "/asyncapi.json"
	req := fmt.Sprintf("GET %s", url)

	client := &http.Client{Timeout: 5 * time.Second}
	start := time.Now()
	resp, err := client.Get(url)
	elapsed := time.Since(start)

	if err != nil {
		return Result{
			ID:      CheckAsyncAPI,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("connection error: %v", err),
			Detail:  fmt.Sprintf("FAILED — %v (%.0fms)", err, float64(elapsed.Milliseconds())),
		}
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return Result{
			ID:       CheckAsyncAPI,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("HTTP %d", resp.StatusCode),
			ErrMsg:   fmt.Sprintf("unexpected status %d", resp.StatusCode),
			Detail:   fmt.Sprintf("FAILED — HTTP %d", resp.StatusCode),
		}
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return Result{ID: CheckAsyncAPI, Passed: false, Request: req, ErrMsg: fmt.Sprintf("read error: %v", err)}
	}

	var doc map[string]interface{}
	if err := json.Unmarshal(body, &doc); err != nil {
		return Result{
			ID:      CheckAsyncAPI,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("JSON parse error: %v", err),
			Detail:  "FAILED — invalid JSON",
		}
	}

	version, _ := doc["asyncapi"].(string)
	if !strings.HasPrefix(version, "3.") {
		return Result{
			ID:       CheckAsyncAPI,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("asyncapi=%q", version),
			ErrMsg:   fmt.Sprintf("expected asyncapi version 3.x, got %q", version),
			Detail:   fmt.Sprintf("FAILED — version %q (expected 3.x)", version),
		}
	}

	return Result{
		ID:       CheckAsyncAPI,
		Passed:   true,
		Request:  req,
		Response: fmt.Sprintf("asyncapi=%s (%.0fms)", version, float64(elapsed.Milliseconds())),
		Detail:   fmt.Sprintf("PASSED — asyncapi %s (%.0fms)", version, float64(elapsed.Milliseconds())),
	}
}
