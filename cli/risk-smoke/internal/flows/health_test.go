package flows_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/naranjax/risk-smoke/internal/config"
	"github.com/naranjax/risk-smoke/internal/flows"
)

func TestHealthCheck_Pass(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/healthz" {
			t.Errorf("unexpected path %q", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.HealthCheck{}
	result := chk.Run(cfg)
	if !result.Passed {
		t.Errorf("expected PASSED, got: %s — %s", result.Detail, result.ErrMsg)
	}
}

func TestHealthCheck_Fail_503(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.HealthCheck{}
	result := chk.Run(cfg)
	if result.Passed {
		t.Error("expected FAILED for 503 response")
	}
}

func TestHealthCheck_Fail_Unreachable(t *testing.T) {
	cfg := config.Default()
	cfg.BaseURL = "http://127.0.0.1:19999" // nothing listening

	chk := &flows.HealthCheck{}
	result := chk.Run(cfg)
	if result.Passed {
		t.Error("expected FAILED for unreachable endpoint")
	}
	if result.ErrMsg == "" {
		t.Error("expected non-empty ErrMsg")
	}
}
