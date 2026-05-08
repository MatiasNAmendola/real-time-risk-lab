package flows_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/riskplatform/risk-smoke/internal/config"
	"github.com/riskplatform/risk-smoke/internal/flows"
)

func TestOpenAPICheck_Pass(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"openapi":"3.1.0","webhooks":{},"paths":{}}`))
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.OpenAPICheck{}
	result := chk.Run(cfg)
	if !result.Passed {
		t.Errorf("expected PASSED, got: %s — %s", result.Detail, result.ErrMsg)
	}
}

func TestOpenAPICheck_Fail_MissingWebhooks(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"openapi":"3.1.0","paths":{}}`))
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.OpenAPICheck{}
	result := chk.Run(cfg)
	if result.Passed {
		t.Error("expected FAILED when 'webhooks' key is absent")
	}
}

func TestAsyncAPICheck_Pass(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"asyncapi":"3.0.0","info":{"title":"Risk Events"}}`))
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.AsyncAPICheck{}
	result := chk.Run(cfg)
	if !result.Passed {
		t.Errorf("expected PASSED, got: %s — %s", result.Detail, result.ErrMsg)
	}
}

func TestAsyncAPICheck_Fail_WrongVersion(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"asyncapi":"2.6.0","info":{}}`))
	}))
	defer srv.Close()

	cfg := config.Default()
	cfg.BaseURL = srv.URL

	chk := &flows.AsyncAPICheck{}
	result := chk.Run(cfg)
	if result.Passed {
		t.Error("expected FAILED for asyncapi version 2.x")
	}
}
