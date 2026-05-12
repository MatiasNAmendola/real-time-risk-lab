# risk-client-go

> Risk engine client SDK for the Go ecosystem. Encapsulates 7 communication channels
> (REST sync, REST batch, SSE, WebSocket, Webhooks, HTTP/SSE events adapter, SQS, Admin).
> Production-ready, semver-compatible, infrastructure-agnostic.

## Install

```bash
go get github.com/riskplatform/risk-client@v1.0.0
```

Requires Go 1.21+.

## Quick start

```go
package main

import (
    "context"
    "fmt"
    "os"
    "time"

    riskclient "github.com/riskplatform/risk-client"
)

func main() {
    ctx := context.Background()
    client, err := riskclient.New(ctx, riskclient.Config{
        Environment: riskclient.Local,
        APIKey:      os.Getenv("RISK_API_KEY"),
        Timeout:     280 * time.Millisecond,
        Retry:       riskclient.ExponentialBackoff(),
    })
    if err != nil {
        panic(err)
    }

    req := riskclient.RiskRequest{TransactionID: "txn-001", Amount: 1500.00, UserID: "user-42", DeviceID: "device-99"}
    decision, err := client.Sync.Evaluate(ctx, req)
    if err != nil {
        panic(err)
    }
    fmt.Println(decision.Outcome) // APPROVE | DECLINE | REVIEW
}
```

## Configuration

### Environments

| Constant | REST base URL | Use for |
|---|---|---|
| `riskclient.Prod` | `https://risk.example.com` | Production traffic |
| `riskclient.Staging` | `https://risk-staging.riskplatform.com` | Pre-prod validation |
| `riskclient.Dev` | `https://risk-dev.riskplatform.com` | Feature branches |
| `riskclient.Local` | `http://localhost:8080` | Local development |

The SDK resolves all downstream URLs (REST endpoints, event adapter paths, SQS queue ARNs,
WebSocket paths) from the `Environment` constant. You never hard-code infrastructure URLs.

### Full config options

```go
client, err := riskclient.New(ctx, riskclient.Config{
    Environment: riskclient.Prod,
    APIKey:      os.Getenv("RISK_API_KEY"),      // required
    Timeout:     280 * time.Millisecond,          // per-request timeout
    Retry: riskclient.RetryConfig{
        Strategy:    riskclient.Exponential,
        MaxAttempts: 3,
        InitialDelay: 100 * time.Millisecond,
        MaxDelay:    2 * time.Second,
    },
    Otel:             riskclient.OtelFromEnv(),   // reads OTEL_EXPORTER_OTLP_ENDPOINT
    EndpointOverride: "http://localhost:8080",    // optional: bypass environment resolution
})
```

### Authentication

The SDK supports two auth modes:

**API key** (recommended for service-to-service):

```go
APIKey: os.Getenv("RISK_API_KEY"),
```

The key is sent as `Authorization: Bearer <key>` on every request. Store it in your
secrets manager (AWS Secrets Manager, Vault, k8s Secret) — never in source code or
committed config files.

**OAuth2 client credentials** (for tenants with IdP integration):

```go
OAuth2: &riskclient.OAuth2Config{
    TokenEndpoint: "https://auth.riskplatform.com/token",
    ClientID:      os.Getenv("OAUTH_CLIENT_ID"),
    ClientSecret:  os.Getenv("OAUTH_CLIENT_SECRET"),
},
```

The SDK handles token refresh automatically before expiry.

## Communication channels

### 1. REST sync — `client.Sync.Evaluate(ctx, req)`

Synchronous point evaluation. Blocks until a `RiskDecision` is returned or the context
deadline fires. Use for transactional flows where you need an immediate decision.

```go
req := riskclient.RiskRequest{
    TransactionID: "txn-001",
    Amount:        1500.00,
    UserID:        "user-42",
    DeviceID:      "device-99",
}

decision, err := client.Sync.Evaluate(ctx, req)
if err != nil {
    return fmt.Errorf("evaluate: %w", err)
}
// decision.Outcome   -> "APPROVE" | "DECLINE" | "REVIEW"
// decision.Score     -> float64 0.0–1.0
// decision.Reason    -> []string
// decision.TraceID   -> string (correlate with OTEL spans)
```

Expected latency: < 300 ms at p99. Returns a typed error on 4xx/5xx (see Error handling).

### 2. REST batch — `client.Sync.EvaluateBatch(ctx, reqs)`

Evaluate a slice of requests in a single HTTP call. Returns one decision per input
in the same order.

```go
requests := []riskclient.RiskRequest{
    {TransactionID: "txn-001", Amount: 500.00, UserID: "user-10", DeviceID: "dev-1"},
    {TransactionID: "txn-002", Amount: 9999.00, UserID: "user-11", DeviceID: "dev-2"},
}

decisions, err := client.Sync.EvaluateBatch(ctx, requests)
if err != nil {
    return err
}
for _, d := range decisions {
    fmt.Printf("%s -> %s\n", d.TransactionID, d.Outcome)
}
```

Prefer batch over N sequential calls when processing queued work. Max batch size: 100.

### 3. Server-Sent Events — `client.Stream.Decisions(ctx, handler)`

Opens a persistent SSE connection and invokes the handler for each event. The connection
auto-reconnects on drop with exponential back-off. Return an error from the handler to
stop consumption; return `nil` to continue.

```go
// Consume indefinitely — blocks until ctx is cancelled or handler returns an error
err := client.Stream.Decisions(ctx, func(ctx context.Context, e riskclient.DecisionEvent) error {
    fmt.Printf("%s -> %s\n", e.TransactionID, e.Outcome)
    return nil // nil continues; any error stops
})

// Bounded consumption with a cancel
ctx, cancel := context.WithCancel(context.Background())
count := 0
err := client.Stream.Decisions(ctx, func(ctx context.Context, e riskclient.DecisionEvent) error {
    processEvent(e)
    count++
    if count >= 50 {
        cancel() // triggers graceful shutdown of the stream
    }
    return nil
})
```

`DecisionEvent` carries: `EventID`, `TransactionID`, `Outcome`, `Score`, `Timestamp`.

### 4. WebSocket — `client.Channel.Open(ctx)`

Bidirectional channel. Returns a `RiskChannel` with `Send`, `Receive`, and `Close`.
The SDK manages heartbeat/ping-pong automatically and re-establishes the connection
on network failure.

```go
ch, err := client.Channel.Open(ctx)
if err != nil {
    return err
}
defer ch.Close()

// Send a request
if err := ch.Send(ctx, req); err != nil {
    return err
}

// Receive the paired response (honours ctx deadline)
receiveCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
defer cancel()

reply, err := ch.Receive(receiveCtx)
if err != nil {
    return err
}
fmt.Println(reply.Outcome)

// Or listen asynchronously
go func() {
    for {
        msg, err := ch.Receive(ctx)
        if err != nil {
            return // context cancelled or channel closed
        }
        handleDecision(msg)
    }
}()
```

### 5. Webhooks — `client.Webhooks`

Register a callback URL that the server calls when decisions match your filter.
Useful for event-driven architectures where you do not want to poll.

```go
// Subscribe
sub, err := client.Webhooks.Subscribe(ctx, "https://my-service.internal/risk-callback", "DECLINE,REVIEW")
if err != nil {
    return err
}
fmt.Println(sub.SubscriptionID)

// List active subscriptions
active, err := client.Webhooks.List(ctx)

// Unsubscribe
err = client.Webhooks.Unsubscribe(ctx, sub.SubscriptionID)

// Verify HMAC signature on incoming callback (call from your HTTP handler)
func handleWebhook(w http.ResponseWriter, r *http.Request) {
    body, _ := io.ReadAll(r.Body)
    sig := r.Header.Get("X-Risk-Signature")
    secret := os.Getenv("WEBHOOK_SECRET")

    if !client.Webhooks.Verify(body, sig, secret) {
        http.Error(w, "invalid signature", http.StatusUnauthorized)
        return
    }

    var decision riskclient.RiskDecision
    if err := json.Unmarshal(body, &decision); err != nil {
        http.Error(w, "bad payload", http.StatusBadRequest)
        return
    }
    processDecline(decision)
    w.WriteHeader(http.StatusOK)
}
```

### 6. Events adapter — `client.Events`

Subscribe to the decision stream via the supported HTTP/SSE adapter. The Go SDK
does **not** open Kafka consumer groups directly against local Tansu 0.6.0 because
that path is blocked by upstream `tansu-io/tansu#668`. Server-side Java/cp-kafka
components remain responsible for Kafka wire interactions.

```go
// Consume decisions — blocks until ctx is cancelled or handler returns an error
err := client.Events.ConsumeDecisions(ctx, "my-consumer-group",
    func(ctx context.Context, e riskclient.DecisionEvent) error {
        processDecision(e)
        return nil
    },
)

// Publish a custom event through HTTP ingress; the platform can fan it into Kafka.
err = client.Events.PublishCustomEvent(ctx, map[string]any{
    "eventType": "FRAUD_SIGNAL",
    "payload":   map[string]any{"userId": "user-42", "signal": "device_mismatch"},
})
```

### 7. SQS queue — `client.Queue`

Post requests to an SQS queue for asynchronous processing, or pull decision results
from the response queue. The SDK manages visibility timeouts and deletion.

```go
// Send a request to the inbound queue
err := client.Queue.SendDecisionRequest(ctx, req)

// Receive and process decisions from the outbound queue
// Blocks until ctx is cancelled or handler returns a non-nil error.
err = client.Queue.ReceiveDecisions(ctx,
    func(ctx context.Context, d riskclient.RiskDecision) error {
        if err := storeResult(d); err != nil {
            return err // message becomes visible again after visibility timeout
        }
        return nil // message deleted from queue
    },
)
```

### 8. Admin operations — `client.Admin`

Manage the rules engine at runtime. Requires an API key with `ADMIN` scope.

```go
// List all active rules
rules, err := client.Admin.ListRules(ctx)
for _, r := range rules {
    fmt.Printf("%s -> %s\n", r.ID, r.Description)
}

// Hot-reload rules from backing store without restart
result, err := client.Admin.ReloadRules(ctx)
fmt.Println(result.RulesLoaded)

// Dry-run a single rule against a request
dryRun, err := client.Admin.TestRule(ctx, "rule-max-amount", req)
fmt.Println(dryRun.WouldTrigger)

// Audit trail
trail, err := client.Admin.RulesAuditTrail(ctx)
```

## Error handling

All SDK operations return typed errors. Use `errors.As` for structured inspection.

```go
import "errors"

decision, err := client.Sync.Evaluate(ctx, req)
if err != nil {
    var timeoutErr *riskclient.TimeoutError
    var authErr *riskclient.AuthError
    var schemaErr *riskclient.SchemaError
    var serverErr *riskclient.ServerError
    var upgradeErr *riskclient.UpgradeRequiredError

    switch {
    case errors.As(err, &timeoutErr):
        // Request exceeded configured timeout. Retries exhausted.
        log.Printf("timeout after %v, traceId=%s", timeoutErr.Elapsed, timeoutErr.TraceID)
        return fallbackDecision(req.TransactionID), nil

    case errors.As(err, &authErr):
        // 401 — API key expired or revoked.
        alertOncall("risk-api-key rotation required")
        return nil, err

    case errors.As(err, &schemaErr):
        // 422 — request rejected by server validation.
        log.Printf("schema mismatch: %v", schemaErr.ValidationErrors)
        return nil, err

    case errors.As(err, &serverErr):
        // 5xx — server-side error.
        if serverErr.StatusCode == 503 {
            return fallbackToCache(req)
        }
        return nil, err

    case errors.As(err, &upgradeErr):
        // 426 — server version is newer than SDK.
        log.Printf("SDK out of date. Server requires SDK >= %s", upgradeErr.MinSDKVersion)
        return nil, err

    default:
        return nil, fmt.Errorf("risk evaluate: %w", err)
    }
}
```

## Retry and timeout

```go
// Exponential back-off (default)
Retry: riskclient.RetryConfig{
    Strategy:     riskclient.Exponential,
    MaxAttempts:  3,
    InitialDelay: 100 * time.Millisecond,
    Multiplier:   2.0,
    MaxDelay:     2 * time.Second,
    RetryOn:      []string{"ServerError"}, // only retry 5xx, not 4xx
}

// Fixed delay
Retry: riskclient.RetryConfig{Strategy: riskclient.Fixed, Delay: 200 * time.Millisecond, MaxAttempts: 2}

// No retry
Retry: riskclient.NoRetry()
```

`Timeout` is a per-attempt budget. Pass a context with a deadline to add an overall
wall-clock budget on top of retry:

```go
ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
defer cancel()
decision, err := client.Sync.Evaluate(ctx, req)
```

Integrate with `github.com/sony/gobreaker` for circuit-breaker patterns.

## Observability

The SDK propagates OpenTelemetry trace context automatically. Every outbound HTTP request
carries `traceparent` and `tracestate` headers from the active span in `ctx`.

```go
// Auto-configure from environment (OTEL_EXPORTER_OTLP_ENDPOINT, etc.)
Otel: riskclient.OtelFromEnv()

// Explicit endpoint
Otel: riskclient.OtelConfig{
    OTLPEndpoint: "http://otel-collector:4318",
    ServiceName:  "my-payment-service",
}
```

If you already bootstrapped an OTEL SDK (`otel.SetTracerProvider(...)`) before calling
`riskclient.New`, the SDK inherits the global tracer provider automatically.

Every `RiskDecision` carries a `TraceID` that matches the server-side span for
end-to-end trace correlation in Jaeger or any OTLP-compatible backend.

## Versioning

This SDK follows [Semantic Versioning](https://semver.org/) with Go module conventions:

- **MAJOR** — breaking API change (module path bumps to `v2`). Server runs both versions
  for 6 months minimum. Migration playbook published in `docs/migrations/`.
- **MINOR** — additive change (new exported func, optional field, new channel).
- **PATCH** — bug fix, performance improvement, dependency update.

Tag and push to release:

```bash
git tag v1.1.0 && git push --tags
```

When the server deprecates a feature it sends `Deprecation: true` and `Sunset: <date>`
response headers. The SDK logs a one-time warning per process and continues working.

## Compatibility matrix

| Server version | SDK v1.0 | SDK v1.1 | SDK v2.0 |
|---|---|---|---|
| Server 1.0 | compatible | compatible | not compatible |
| Server 1.1 | compatible (deprecated features unused) | compatible | not compatible |
| Server 2.0 | not compatible | compatible (with /v1 fallback) | compatible |

Full matrix: [docs/22-client-sdks.md](../../docs/22-client-sdks.md).

## Examples

### Sync evaluation with fallback

```go
func decide(ctx context.Context, client *riskclient.Client, req riskclient.RiskRequest) (riskclient.RiskDecision, error) {
    d, err := client.Sync.Evaluate(ctx, req)
    if err != nil {
        var timeoutErr *riskclient.TimeoutError
        var serverErr *riskclient.ServerError
        if errors.As(err, &timeoutErr) || errors.As(err, &serverErr) {
            log.Println("risk engine unavailable, using fallback policy")
            return riskclient.FallbackDecision(req.TransactionID), nil
        }
        return riskclient.RiskDecision{}, err
    }
    return d, nil
}
```

### Async batch with goroutines

```go
results := make(chan []riskclient.RiskDecision, 1)
go func() {
    decisions, err := client.Sync.EvaluateBatch(ctx, requests)
    if err != nil {
        log.Printf("batch error: %v", err)
        return
    }
    results <- decisions
}()
decisions := <-results
```

### Webhook integration (net/http handler)

```go
http.HandleFunc("/risk-callback", func(w http.ResponseWriter, r *http.Request) {
    body, _ := io.ReadAll(r.Body)
    if !client.Webhooks.Verify(body, r.Header.Get("X-Risk-Signature"), os.Getenv("WEBHOOK_SECRET")) {
        http.Error(w, "unauthorized", http.StatusUnauthorized)
        return
    }
    var d riskclient.RiskDecision
    json.Unmarshal(body, &d)
    if d.Outcome == "DECLINE" {
        triggerFraudAlert(d)
    }
    w.WriteHeader(http.StatusOK)
})
```

### Events adapter with context cancellation

```go
ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
defer cancel()

if err := client.Events.ConsumeDecisions(ctx, "fraud-alert-group",
    func(ctx context.Context, e riskclient.DecisionEvent) error {
        if e.Outcome == "DECLINE" {
            return alertFraudTeam(ctx, e)
        }
        return nil
    },
); err != nil && !errors.Is(err, context.Canceled) {
    log.Fatal(err)
}
```

### Full E2E: Go test

```go
func TestEvaluateLowAmountReturnsApprove(t *testing.T) {
    ctx := context.Background()
    client, err := riskclient.New(ctx, riskclient.Config{
        Environment: riskclient.Local,
        APIKey:      System.getenv("RISK_CLIENT_API_KEY"),
        Timeout:     500 * time.Millisecond,
    })
    require.NoError(t, err)

    req := riskclient.RiskRequest{TransactionID: "txn-test-001", Amount: 100.00, UserID: "user-1", DeviceID: "dev-1"}
    decision, err := client.Sync.Evaluate(ctx, req)
    require.NoError(t, err)
    assert.Equal(t, "APPROVE", decision.Outcome)
    assert.NotEmpty(t, decision.TraceID)
}
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `*TimeoutError` on first call | Server not reachable or wrong `Environment` | Call `client.Sync.Health(ctx)` to verify connectivity |
| `*AuthError` (401) | API key expired or wrong env key | Rotate key in secrets manager; check `RISK_API_KEY` env var |
| `*UpgradeRequiredError` (426) | Server version newer than SDK | Run `go get github.com/riskplatform/risk-client@latest` |
| `riskclient.New` returns error | Config validation failed | Check that `APIKey` is non-empty and `Environment` is a valid constant |
| SSE `ConsumeDecisions` handler never fires | Proxy buffers SSE response | Set `X-Accel-Buffering: no` header on proxy; check firewall allows keep-alive |
| `*ChannelClosedError` on WebSocket receive | Proxy strips `Upgrade: websocket` | Configure reverse proxy for WebSocket passthrough |
| `*SchemaError` (422) | Request field invalid or missing | Log `err.ValidationErrors` and compare to `RiskRequest` struct tags |
| Events adapter exits immediately | Context already cancelled before call | Pass a fresh `context.Background()` with cancellation tied to `SIGTERM` |
| SQS `ReceiveDecisions` handler never fires | Queue empty or wrong region | Confirm queue ARN and region match the configured `Environment` |
| `*ServerError` (503) | Server under load or restarting | Enable retry policy; add circuit breaker via `gobreaker` |
| Admin calls return 403 | API key lacks ADMIN scope | Request ADMIN-scoped key from platform team |
| `Deprecation: true` warning in logs | Server feature deprecated | Plan migration; see `Sunset` date in header for deadline |
| Race condition in concurrent `ch.Receive` | Multiple goroutines reading same channel | Each goroutine should use its own `RiskChannel` instance |

## Migrating from other clients

This is the v1 SDK. No migration from a previous SDK is required.

If you previously made raw `net/http` calls to the risk engine, replace them:

```go
// Before (manual HTTP)
resp, err := http.Post("http://localhost:8080/risk/evaluate", "application/json", body)

// After (SDK)
decision, err := client.Sync.Evaluate(ctx, req)
```

The SDK handles auth, retry, timeout, tracing, schema evolution, and context propagation
automatically.

## Contributing

Bug reports and feature requests: open a GitHub issue.

Development setup:

```bash
cd sdks/risk-client-go
go build ./...
go test ./...
go test -tags=integration ./...   # requires Docker
go vet ./...
golangci-lint run
```

All PRs require passing unit tests and at least one new integration test for new channels.

## License

Apache-2.0

## Related docs

- [docs/22-client-sdks.md](../../docs/22-client-sdks.md) — Design rationale, SemVer policy, deprecation rules.
- [API specs](http://localhost:8080/openapi.json) — OpenAPI 3.1 (REST) + AsyncAPI 3.0 (events/WS/webhooks).
- [Cross-SDK contract tests](../contract-test/) — proves Java/TypeScript/Go agree on every channel.
- [sdks/README.md](../README.md) — Cross-language equivalence table.
