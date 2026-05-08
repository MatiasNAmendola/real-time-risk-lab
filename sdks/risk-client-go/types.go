// Package riskclient provides the Risk Decision Platform client SDK for Go.
package riskclient

import (
	"context"
	"time"
)

// Environment selects deployment coordinates.
type Environment int

const (
	Prod Environment = iota
	Staging
	Dev
	Local
)

type envCoords struct {
	restBaseURL string
	kafkaBroker string
	sqsQueueURL string
}

var envMap = map[Environment]envCoords{
	Prod: {
		restBaseURL: "https://risk.riskplatform.com",
		kafkaBroker: "kafka.riskplatform.com:9092",
		sqsQueueURL: "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-prod",
	},
	Staging: {
		restBaseURL: "https://risk-staging.riskplatform.com",
		kafkaBroker: "kafka-staging.riskplatform.com:9092",
		sqsQueueURL: "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-staging",
	},
	Dev: {
		restBaseURL: "https://risk-dev.riskplatform.com",
		kafkaBroker: "kafka-dev.riskplatform.com:9092",
		sqsQueueURL: "https://sqs.us-east-1.amazonaws.com/123456789/risk-decisions-dev",
	},
	Local: {
		restBaseURL: "http://localhost:8080",
		kafkaBroker: "localhost:9092",
		sqsQueueURL: "http://localhost:4566/000000000000/risk-decisions",
	},
}

// RiskRequest is the canonical evaluation request.
// Field names align with the deployed Vert.x server contract
// (see poc/java-vertx-distributed/shared RiskRequest and openapi.yaml).
type RiskRequest struct {
	TransactionID  string `json:"transactionId"`
	CustomerID     string `json:"customerId"`
	AmountCents    int64  `json:"amountCents"`
	CorrelationID  string `json:"correlationId,omitempty"`
	IdempotencyKey string `json:"idempotencyKey,omitempty"`
	NewDevice      bool   `json:"newDevice,omitempty"`
	// Optional/extension fields tolerated by server but not in core contract.
	DeviceID   string `json:"deviceId,omitempty"`
	MerchantID string `json:"merchantId,omitempty"`
	Channel    string `json:"channel,omitempty"`
}

// RiskDecision is the evaluation outcome.
type RiskDecision struct {
	TransactionID string        `json:"transactionId"`
	Decision      string        `json:"decision"`
	Reason        string        `json:"reason"`
	Elapsed       time.Duration `json:"elapsedMs,omitempty"`
}

func (d *RiskDecision) IsApproved() bool      { return d.Decision == "APPROVE" }
func (d *RiskDecision) IsDeclined() bool      { return d.Decision == "DECLINE" }
func (d *RiskDecision) RequiresReview() bool  { return d.Decision == "REVIEW" }

// DecisionEvent is emitted for every evaluation.
type DecisionEvent struct {
	EventID       string    `json:"eventId"`
	EventType     string    `json:"eventType"`
	EventVersion  int       `json:"eventVersion"`
	OccurredAt    time.Time `json:"occurredAt"`
	CorrelationID string    `json:"correlationId"`
	TransactionID string    `json:"transactionId"`
	Decision      string    `json:"decision"`
	Reason        string    `json:"reason"`
}

// HealthStatus is the liveness response.
type HealthStatus struct {
	Status  string `json:"status"`
	Version string `json:"version,omitempty"`
}

func (h *HealthStatus) IsUp() bool { return h.Status == "UP" }

// RuleInfo describes a single fraud rule.
type RuleInfo struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Enabled     bool   `json:"enabled"`
	Priority    int    `json:"priority"`
}

// Subscription represents a registered webhook.
type Subscription struct {
	ID          string    `json:"id"`
	CallbackURL string    `json:"callbackUrl"`
	EventFilter string    `json:"eventFilter"`
	CreatedAt   time.Time `json:"createdAt"`
}

// RetryPolicy controls retry behaviour for the REST channel.
type RetryPolicy struct {
	MaxAttempts  int
	InitialDelay time.Duration
	Multiplier   float64
}

// ExponentialBackoff returns the default retry policy (3 attempts, 100 ms, x2).
func ExponentialBackoff() RetryPolicy {
	return RetryPolicy{MaxAttempts: 3, InitialDelay: 100 * time.Millisecond, Multiplier: 2.0}
}

// NoRetry returns a policy that fails on the first error.
func NoRetry() RetryPolicy {
	return RetryPolicy{MaxAttempts: 1}
}

// Config holds all client configuration.
type Config struct {
	Environment  Environment
	APIKey       string
	Timeout      time.Duration
	Retry        RetryPolicy
	OTLPEndpoint string
}

// DecisionHandler is the callback type for Kafka / SSE consumers.
type DecisionHandler func(ctx context.Context, event DecisionEvent) error
