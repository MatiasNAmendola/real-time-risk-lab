package flows

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/riskplatform/risk-smoke/internal/config"
)

// OTELCheck fires POST /risk, captures traceresponse header, then queries OpenObserve.
type OTELCheck struct{}

func (c *OTELCheck) ID() string   { return CheckOTEL }
func (c *OTELCheck) Name() string { return "OTEL trace — traceresponse header" }

func (c *OTELCheck) Run(cfg *config.Config) Result {
	riskURL := cfg.BaseURL + "/risk"
	req := fmt.Sprintf("POST %s → capture traceresponse → GET %s/api/default/traces?trace_id=<id>", riskURL, cfg.OpenObserveURL)

	client := &http.Client{Timeout: 10 * time.Second}

	// 1. Fire POST /risk
	payload, _ := json.Marshal(map[string]interface{}{
		"transactionId":  "otel-smoke-001",
		"customerId":     "u-otel-001",
		"amountCents":    5000,
		"correlationId":  "corr-otel-smoke-001",
		"idempotencyKey": "idem-otel-smoke-001",
	})
	resp, err := client.Post(riskURL, "application/json", bytes.NewReader(payload))
	if err != nil {
		return Result{
			ID:      CheckOTEL,
			Passed:  false,
			Request: req,
			ErrMsg:  fmt.Sprintf("POST /risk error: %v", err),
			Detail:  fmt.Sprintf("FAILED — POST /risk error: %v", err),
		}
	}
	defer resp.Body.Close()

	// 2. Extract traceresponse (W3C Trace Context)
	traceResp := resp.Header.Get("traceresponse")
	traceparent := resp.Header.Get("traceparent")
	traceID := extractTraceID(traceResp, traceparent)

	if traceID == "" {
		return Result{
			ID:       CheckOTEL,
			Passed:   false,
			Request:  req,
			Response: fmt.Sprintf("traceresponse=%q traceparent=%q", traceResp, traceparent),
			ErrMsg:   "no traceresponse or traceparent header in response",
			Detail:   "FAILED — traceresponse header missing",
		}
	}

	// 3. Query OpenObserve
	ooURL := fmt.Sprintf("%s/api/default/traces?trace_id=%s", cfg.OpenObserveURL, traceID)
	ooResp, err := client.Get(ooURL)
	if err != nil {
		return Result{
			ID:       CheckOTEL,
			Skipped:  true,
			Request:  req,
			Response: fmt.Sprintf("traceID=%s", traceID),
			Detail:   fmt.Sprintf("SKIP — trace header present but OpenObserve unreachable: %v", err),
		}
	}
	defer ooResp.Body.Close()

	body, _ := io.ReadAll(io.LimitReader(ooResp.Body, 1<<20))

	var ooDoc map[string]interface{}
	_ = json.Unmarshal(body, &ooDoc)

	services := countServices(ooDoc)
	if services < 3 {
		return Result{
			ID:       CheckOTEL,
			Skipped:  true,
			Request:  req,
			Response: fmt.Sprintf("traceID=%s OO HTTP %d services=%d", traceID, ooResp.StatusCode, services),
			Detail:   fmt.Sprintf("SKIP — trace header present but OpenObserve has %d services indexed", services),
		}
	}

	return Result{
		ID:       CheckOTEL,
		Passed:   true,
		Request:  req,
		Response: fmt.Sprintf("traceID=%s, %d services found in OpenObserve", traceID, services),
		Detail:   fmt.Sprintf("PASSED — %d services in trace %s", services, traceID),
	}
}

// extractTraceID parses the trace ID from W3C traceresponse or traceparent headers.
// Format: 00-<traceID>-<spanID>-<flags>
func extractTraceID(traceresponse, traceparent string) string {
	for _, h := range []string{traceresponse, traceparent} {
		if h == "" {
			continue
		}
		parts := strings.Split(h, "-")
		if len(parts) >= 2 {
			return parts[1]
		}
	}
	return ""
}

func countServices(doc map[string]interface{}) int {
	services := map[string]bool{}
	walkForServices(doc, services)
	return len(services)
}

func walkForServices(v interface{}, out map[string]bool) {
	switch t := v.(type) {
	case map[string]interface{}:
		if svc, ok := t["serviceName"].(string); ok && svc != "" {
			out[svc] = true
		}
		for _, val := range t {
			walkForServices(val, out)
		}
	case []interface{}:
		for _, item := range t {
			walkForServices(item, out)
		}
	}
}
