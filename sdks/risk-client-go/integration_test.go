//go:build integration
// +build integration

package riskclient_test

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"testing"
	"time"

	riskclient "github.com/riskplatform/risk-client"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// Suite-level setup / teardown
// ---------------------------------------------------------------------------

const composeFile = "../../poc/vertx-layer-as-pod-eventbus/docker-compose.yml"

// baseURL is overridable via RISK_BASE_URL for cases where the stack is
// started externally (e.g. CI pipeline).
func baseURL() string {
	if v := os.Getenv("RISK_BASE_URL"); v != "" {
		return v
	}
	return "http://localhost:8080"
}

// dockerStarted tracks whether TestMain brought up compose so teardown is
// conditional.
var dockerStarted bool

func TestMain(m *testing.M) {
	if os.Getenv("RISK_BASE_URL") == "" {
		cmd := exec.Command("docker", "compose",
			"-f", composeFile,
			"up", "-d", "--wait")
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err := cmd.Run(); err == nil {
			dockerStarted = true
		}
	}

	// Poll healthz until server is ready (up to 60 s).
	deadline := time.Now().Add(60 * time.Second)
	for time.Now().Before(deadline) {
		resp, err := http.Get(baseURL() + "/healthz") //nolint:noctx
		if err == nil && resp.StatusCode == 200 {
			_ = resp.Body.Close()
			break
		}
		if resp != nil {
			_ = resp.Body.Close()
		}
		time.Sleep(2 * time.Second)
	}

	code := m.Run()

	if dockerStarted {
		down := exec.Command("docker", "compose",
			"-f", composeFile,
			"down")
		down.Stdout = os.Stdout
		down.Stderr = os.Stderr
		_ = down.Run()
	}

	os.Exit(code)
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

func newClient(t *testing.T) *riskclient.Client {
	t.Helper()
	cfg := riskclient.Config{
		Environment: riskclient.Local,
		APIKey:      envOrFallback("RISK_CLIENT_API_KEY", "change-me-client-api-key"),
		Timeout:     10 * time.Second,
		Retry:       riskclient.ExponentialBackoff(),
	}
	client := riskclient.NewWithServerOverride(cfg, baseURL(), nil)
	require.NotNil(t, client)
	return client
}

func envOrFallback(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func sampleReq(txID string, amount float64) riskclient.RiskRequest {
	return riskclient.RiskRequest{
		TransactionID: txID,
		CorrelationID: fmt.Sprintf("corr-%s", txID),
		CustomerID:    "cust-1",
		Amount:        amount,
		Currency:      "ARS",
		DeviceID:      "known-dev-1",
		MerchantID:    "merch-1",
		Channel:       "WEB",
	}
}

// ---------------------------------------------------------------------------
// Tests — functional (evaluate)
// ---------------------------------------------------------------------------

func TestIntegration_EvaluateLowAmount_ReturnsApprove(t *testing.T) {
	client := newClient(t)
	decision, err := client.Sync.Evaluate(context.Background(), sampleReq("tx-go-1", 1.0))
	require.NoError(t, err)
	require.Equal(t, "APPROVE", decision.Decision)
}

func TestIntegration_EvaluateHighAmount_ReturnsDeclineOrReview(t *testing.T) {
	client := newClient(t)
	decision, err := client.Sync.Evaluate(context.Background(), sampleReq("tx-go-2", 900_000.0))
	require.NoError(t, err)
	require.Contains(t, []string{"DECLINE", "REVIEW"}, decision.Decision)
}

func TestIntegration_EvaluateBatch_ReturnsOneDecisionPerRequest(t *testing.T) {
	client := newClient(t)
	batch := []riskclient.RiskRequest{
		sampleReq("tx-go-b1", 1.0),
		sampleReq("tx-go-b2", 2.0),
		sampleReq("tx-go-b3", 3.0),
	}
	decisions, err := client.Sync.EvaluateBatch(context.Background(), batch)
	require.NoError(t, err)
	require.Len(t, decisions, 3)
	for _, d := range decisions {
		require.Contains(t, []string{"APPROVE", "DECLINE", "REVIEW"}, d.Decision)
	}
}

func TestIntegration_Idempotency_SameTransactionIDReturnsSameDecision(t *testing.T) {
	client := newClient(t)
	txID := fmt.Sprintf("tx-go-idem-%d", time.Now().UnixNano())
	first, err := client.Sync.Evaluate(context.Background(), sampleReq(txID, 1.0))
	require.NoError(t, err)
	second, err := client.Sync.Evaluate(context.Background(), sampleReq(txID, 1.0))
	require.NoError(t, err)
	require.Equal(t, first.Decision, second.Decision)
	require.Equal(t, first.Reason, second.Reason)
}

// ---------------------------------------------------------------------------
// Tests — health
// ---------------------------------------------------------------------------

func TestIntegration_Health_ReturnsUp(t *testing.T) {
	client := newClient(t)
	status, err := client.Sync.Health(context.Background())
	require.NoError(t, err)
	require.True(t, status.IsUp())
	require.Equal(t, "UP", status.Status)
}

// ---------------------------------------------------------------------------
// Tests — webhooks
// ---------------------------------------------------------------------------

func TestIntegration_WebhookSubscribe_ReturnsPopulatedSubscription(t *testing.T) {
	client := newClient(t)
	sub, err := client.Webhooks.Subscribe(context.Background(), "http://localhost:9999/cb-go", "DECLINE")
	require.NoError(t, err)
	require.NotEmpty(t, sub.ID)
	require.Equal(t, "http://localhost:9999/cb-go", sub.CallbackURL)
	require.Equal(t, "DECLINE", sub.EventFilter)
}

func TestIntegration_WebhookList_IncludesRegisteredSubscription(t *testing.T) {
	client := newClient(t)
	url := fmt.Sprintf("http://localhost:9997/cb-go-list-%d", time.Now().UnixNano())
	_, err := client.Webhooks.Subscribe(context.Background(), url, "REVIEW")
	require.NoError(t, err)
	list, err := client.Webhooks.List(context.Background())
	require.NoError(t, err)
	require.NotEmpty(t, list)
	found := false
	for _, s := range list {
		if s.CallbackURL == url {
			found = true
			break
		}
	}
	require.True(t, found, "registered subscription not found in list")
}

// ---------------------------------------------------------------------------
// Tests — admin
// ---------------------------------------------------------------------------

func TestIntegration_AdminListRules_ReturnsAtLeastOneEnabledRule(t *testing.T) {
	client := newClient(t)
	rules, err := client.Admin.ListRules(context.Background())
	require.NoError(t, err)
	require.NotEmpty(t, rules)
	enabledCount := 0
	for _, r := range rules {
		require.NotEmpty(t, r.ID)
		require.NotEmpty(t, r.Name)
		if r.Enabled {
			enabledCount++
		}
	}
	require.Greater(t, enabledCount, 0)
}

func TestIntegration_AdminTestRule_ReturnsValidDecision(t *testing.T) {
	client := newClient(t)
	decision, err := client.Admin.TestRule(context.Background(), sampleReq("tx-go-admin-1", 1.0))
	require.NoError(t, err)
	require.Contains(t, []string{"APPROVE", "DECLINE", "REVIEW"}, decision.Decision)
	require.NotEmpty(t, decision.Reason)
}
