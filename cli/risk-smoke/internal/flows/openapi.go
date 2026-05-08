package flows

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

// OpenAPICheck fetches /openapi.json and validates "webhooks" key presence.
type OpenAPICheck struct{}

func (c *OpenAPICheck) ID() string   { return CheckOpenAPI }
func (c *OpenAPICheck) Name() string { return "OpenAPI — validate /openapi.json" }

func (c *OpenAPICheck) Run(cfg *config.Config) Result {
	url := cfg.BaseURL + "/openapi.json"
	req := fmt.Sprintf("GET %s", url)

	client := &http.Client{Timeout: 5 * time.Second}
	start := time.Now()
	resp, err := client.Get(url)
	elapsed := time.Since(start)

	if err != nil {
		return Result{
			ID:      CheckOpenAPI,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("connection error: %v", err),
			Detail:  fmt.Sprintf("FAILED — %v (%.0fms)", err, float64(elapsed.Milliseconds())),
		}
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return Result{
			ID:       CheckOpenAPI,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("HTTP %d", resp.StatusCode),
			ErrMsg:   fmt.Sprintf("unexpected status %d", resp.StatusCode),
			Detail:   fmt.Sprintf("FAILED — HTTP %d (%.0fms)", resp.StatusCode, float64(elapsed.Milliseconds())),
		}
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return Result{ID: CheckOpenAPI, Passed: false, Request: req, ErrMsg: fmt.Sprintf("read error: %v", err)}
	}

	var doc map[string]interface{}
	if err := json.Unmarshal(body, &doc); err != nil {
		return Result{
			ID:      CheckOpenAPI,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("JSON parse error: %v", err),
			Detail:  "FAILED — invalid JSON",
		}
	}

	if _, ok := doc["webhooks"]; !ok {
		return Result{
			ID:       CheckOpenAPI,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("keys: %v", keys(doc)),
			ErrMsg:   "missing 'webhooks' key in OpenAPI document",
			Detail:   "FAILED — 'webhooks' key not found",
		}
	}

	return Result{
		ID:       CheckOpenAPI,
		Passed:   true,
		Request:  req,
		Response: fmt.Sprintf("HTTP 200, webhooks key present (%.0fms)", float64(elapsed.Milliseconds())),
		Detail:   fmt.Sprintf("PASSED — webhooks key present (%.0fms)", float64(elapsed.Milliseconds())),
	}
}

func keys(m map[string]interface{}) []string {
	ks := make([]string, 0, len(m))
	for k := range m {
		ks = append(ks, k)
	}
	return ks
}
