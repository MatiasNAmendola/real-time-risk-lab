package riskclient_test

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"
	riskclient "github.com/riskplatform/risk-client"
)

// ---------------------------------------------------------------------------
// Mock SQS
// ---------------------------------------------------------------------------

type mockSQS struct {
	sendCalls int
	messages  []types.Message
}

func (m *mockSQS) SendMessage(_ context.Context, _ *sqs.SendMessageInput, _ ...func(*sqs.Options)) (*sqs.SendMessageOutput, error) {
	m.sendCalls++
	return &sqs.SendMessageOutput{MessageId: aws.String("msg-1")}, nil
}

func (m *mockSQS) ReceiveMessage(_ context.Context, _ *sqs.ReceiveMessageInput, _ ...func(*sqs.Options)) (*sqs.ReceiveMessageOutput, error) {
	return &sqs.ReceiveMessageOutput{Messages: m.messages}, nil
}

func (m *mockSQS) DeleteMessage(_ context.Context, _ *sqs.DeleteMessageInput, _ ...func(*sqs.Options)) (*sqs.DeleteMessageOutput, error) {
	return &sqs.DeleteMessageOutput{}, nil
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

func sampleRequest() riskclient.RiskRequest {
	return riskclient.RiskRequest{
		TransactionID:  "txn-001",
		CustomerID:     "cust-001",
		AmountCents:    1000,
		CorrelationID:  "corr-001",
		IdempotencyKey: "idem-001",
	}
}

func sampleDecision() riskclient.RiskDecision {
	return riskclient.RiskDecision{TransactionID: "txn-001", Decision: "APPROVE", Reason: "low risk"}
}

func jsonHandler(status int, body any) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(body)
	}
}

func overrideClient(ts *httptest.Server, sqsMock riskclient.SQSClientAPI) *riskclient.Client {
	if sqsMock == nil {
		sqsMock = &mockSQS{}
	}
	cfg := riskclient.Config{
		APIKey:  os.Getenv("RISK_CLIENT_API_KEY"),
		Timeout: 2 * time.Second,
		Retry:   riskclient.NoRetry(),
	}
	return riskclient.NewWithServerOverride(cfg, ts.URL, sqsMock)
}

func computeHMAC(secret string, payload []byte) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write(payload)
	return hex.EncodeToString(mac.Sum(nil))
}

// ---------------------------------------------------------------------------
// Tests — table driven where appropriate
// ---------------------------------------------------------------------------

func TestSync_Evaluate_Approve(t *testing.T) {
	ts := httptest.NewServer(jsonHandler(200, sampleDecision()))
	defer ts.Close()

	client := overrideClient(ts, nil)
	dec, err := client.Sync.Evaluate(context.Background(), sampleRequest())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !dec.IsApproved() {
		t.Errorf("expected APPROVE, got %s", dec.Decision)
	}
}

func TestSync_Evaluate_Decline(t *testing.T) {
	d := riskclient.RiskDecision{TransactionID: "txn-002", Decision: "DECLINE", Reason: "high risk"}
	ts := httptest.NewServer(jsonHandler(200, d))
	defer ts.Close()

	result, err := overrideClient(ts, nil).Sync.Evaluate(context.Background(), sampleRequest())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsDeclined() {
		t.Errorf("expected DECLINE, got %s", result.Decision)
	}
}

func TestSync_EvaluateBatch(t *testing.T) {
	batch := []riskclient.RiskDecision{
		{TransactionID: "t1", Decision: "APPROVE", Reason: "ok"},
		{TransactionID: "t2", Decision: "REVIEW", Reason: "manual"},
	}
	ts := httptest.NewServer(jsonHandler(200, batch))
	defer ts.Close()

	results, err := overrideClient(ts, nil).Sync.EvaluateBatch(context.Background(), []riskclient.RiskRequest{sampleRequest(), sampleRequest()})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(results) != 2 {
		t.Errorf("expected 2 results, got %d", len(results))
	}
	if !results[1].RequiresReview() {
		t.Errorf("expected second result to require review")
	}
}

func TestSync_Health(t *testing.T) {
	ts := httptest.NewServer(jsonHandler(200, riskclient.HealthStatus{Status: "UP", Version: "1.0.0"}))
	defer ts.Close()

	status, err := overrideClient(ts, nil).Sync.Health(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !status.IsUp() {
		t.Errorf("expected UP, got %s", status.Status)
	}
}

func TestSync_Evaluate_ServerError(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(503)
	}))
	defer ts.Close()

	_, err := overrideClient(ts, nil).Sync.Evaluate(context.Background(), sampleRequest())
	if err == nil {
		t.Fatal("expected error on 503")
	}
}

func TestWebhooks_Subscribe(t *testing.T) {
	sub := riskclient.Subscription{ID: "sub-1", CallbackURL: "http://cb/hook", EventFilter: "DECLINE"}
	ts := httptest.NewServer(jsonHandler(200, sub))
	defer ts.Close()

	result, err := overrideClient(ts, nil).Webhooks.Subscribe(context.Background(), "http://cb/hook", "DECLINE")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.ID != "sub-1" {
		t.Errorf("expected sub-1, got %s", result.ID)
	}
}

func TestWebhooks_List(t *testing.T) {
	subs := []riskclient.Subscription{
		{ID: "s1", CallbackURL: "http://a", EventFilter: "DECLINE"},
		{ID: "s2", CallbackURL: "http://b", EventFilter: "REVIEW"},
	}
	ts := httptest.NewServer(jsonHandler(200, subs))
	defer ts.Close()

	list, err := overrideClient(ts, nil).Webhooks.List(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(list) != 2 {
		t.Errorf("expected 2 subscriptions, got %d", len(list))
	}
}

func TestWebhooks_Verify_Valid(t *testing.T) {
	ts := httptest.NewServer(http.NotFoundHandler())
	defer ts.Close()

	payload := []byte(`{"decision":"DECLINE"}`)
	secret := getenvDefault("RISK_WEBHOOK_TEST_SECRET", "change-me-webhook-secret")
	sig := computeHMAC(secret, payload)

	if !overrideClient(ts, nil).Webhooks.Verify(payload, sig, secret) {
		t.Error("expected Verify to return true for valid signature")
	}
}

func TestWebhooks_Verify_Invalid(t *testing.T) {
	ts := httptest.NewServer(http.NotFoundHandler())
	defer ts.Close()

	if overrideClient(ts, nil).Webhooks.Verify([]byte("data"), "badsig", "secret") {
		t.Error("expected Verify to return false for invalid signature")
	}
}

func TestAdmin_ListRules(t *testing.T) {
	rules := []riskclient.RuleInfo{{ID: "r1", Name: "high-amount", Enabled: true, Priority: 1}}
	ts := httptest.NewServer(jsonHandler(200, rules))
	defer ts.Close()

	list, err := overrideClient(ts, nil).Admin.ListRules(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if list[0].Name != "high-amount" {
		t.Errorf("expected high-amount, got %s", list[0].Name)
	}
}

func TestAdmin_TestRule(t *testing.T) {
	expected := riskclient.RiskDecision{TransactionID: "t1", Decision: "DECLINE", Reason: "rule match"}
	ts := httptest.NewServer(jsonHandler(200, expected))
	defer ts.Close()

	result, err := overrideClient(ts, nil).Admin.TestRule(context.Background(), sampleRequest())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsDeclined() {
		t.Errorf("expected DECLINE")
	}
}

func TestQueue_SendDecisionRequest(t *testing.T) {
	sqsMock := &mockSQS{}
	ts := httptest.NewServer(http.NotFoundHandler())
	defer ts.Close()

	client := overrideClient(ts, sqsMock)
	err := client.Queue.SendDecisionRequest(context.Background(), sampleRequest())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if sqsMock.sendCalls != 1 {
		t.Errorf("expected 1 send call, got %d", sqsMock.sendCalls)
	}
}

func TestQueue_ReceiveDecisions(t *testing.T) {
	dec := sampleDecision()
	b, _ := json.Marshal(dec)
	sqsMock := &mockSQS{
		messages: []types.Message{
			{Body: aws.String(string(b)), ReceiptHandle: aws.String("rh-1")},
		},
	}

	ts := httptest.NewServer(http.NotFoundHandler())
	defer ts.Close()
	client := overrideClient(ts, sqsMock)

	var received []riskclient.RiskDecision
	count, err := client.Queue.ReceiveDecisions(context.Background(), func(_ context.Context, d riskclient.RiskDecision) error {
		received = append(received, d)
		return nil
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if count != 1 {
		t.Errorf("expected 1 processed, got %d", count)
	}
	if received[0].Decision != "APPROVE" {
		t.Errorf("expected APPROVE, got %s", received[0].Decision)
	}
}

func TestEnvironment_LocalBuildsWithoutPanic(t *testing.T) {
	cfg := riskclient.Config{
		Environment: riskclient.Local,
		APIKey:      "k",
	}
	client := riskclient.NewWithSQS(cfg, &mockSQS{})
	if client == nil {
		t.Fatal("expected non-nil client")
	}
}

func getenvDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
