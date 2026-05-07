package flows_test

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/naranjax/risk-smoke/internal/config"
	"github.com/naranjax/risk-smoke/internal/flows"
)

func TestRESTCheck_Pass(t *testing.T) {
	count := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/risk" {
			t.Errorf("unexpected %s %s", r.Method, r.URL.Path)
		}
		_, _ = io.ReadAll(r.Body)
		count++
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"decision":"APPROVE","correlationId":"c-001"}`))
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.RESTCheck{}
	result := chk.Run(cfg)
	if !result.Passed {
		t.Errorf("expected PASSED, got: %s — %s", result.Detail, result.ErrMsg)
	}
	if count != 5 {
		t.Errorf("expected 5 requests, got %d", count)
	}
	if !strings.Contains(result.Latency, "p50=") {
		t.Errorf("expected latency stats in Latency field, got %q", result.Latency)
	}
}

func TestRESTCheck_Fail_Unreachable(t *testing.T) {
	cfg := config.Default()
	cfg.BaseURL = "http://127.0.0.1:19999"

	chk := &flows.RESTCheck{}
	result := chk.Run(cfg)
	if result.Passed {
		t.Error("expected FAILED for unreachable endpoint")
	}
}
