# risk-client-java

> Risk engine client SDK for the Java ecosystem. Encapsulates 7 communication channels
> (REST sync, REST batch, SSE, WebSocket, Webhooks, Kafka, SQS, Admin).
> Production-ready, semver-compatible, infrastructure-agnostic.

## Install

### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.riskplatform.poc:risk-client:1.0.0-SNAPSHOT")
}
```

### Gradle

```xml
<dependency>
    <groupId>io.riskplatform.poc</groupId>
    <artifactId>risk-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Requires Java 21+.

## Quick start

```java
RiskClient client = RiskClient.builder()
    .environment(Environment.LOCAL)
    .apiKey(System.getenv("RISK_API_KEY"))
    .timeout(Duration.ofMillis(280))
    .retry(RetryPolicy.exponentialBackoff())
    .build();

RiskRequest req = new RiskRequest("txn-001", 1500.00, "user-42", "device-99");
RiskDecision decision = client.sync().evaluate(req);
System.out.println(decision.outcome()); // APPROVE | DECLINE | REVIEW
```

## Configuration

### Environments

| Enum value | REST base URL | Use for |
|---|---|---|
| `Environment.PROD` | `https://risk.example.com` | Production traffic |
| `Environment.STAGING` | `https://risk-staging.riskplatform.com` | Pre-prod validation |
| `Environment.DEV` | `https://risk-dev.riskplatform.com` | Feature branches |
| `Environment.LOCAL` | `http://localhost:8080` | Local development |

The SDK resolves all downstream URLs (REST endpoints, Kafka brokers, SQS queue ARNs,
WebSocket paths) from the `Environment` value. You never hard-code infrastructure URLs.

### Full builder options

```java
RiskClient client = RiskClient.builder()
    .environment(Environment.PROD)
    .apiKey(secrets.get("risk-engine-key"))          // required
    .timeout(Duration.ofMillis(280))                  // per-request timeout
    .retry(RetryPolicy.exponentialBackoff()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(100))
        .maxDelay(Duration.ofSeconds(2)))
    .observability(OtelOptions.fromEnv())             // reads OTEL_EXPORTER_OTLP_ENDPOINT
    .endpointOverride("http://localhost:8080")        // optional: bypass environment resolution
    .build();
```

### Authentication

The SDK supports two auth modes:

**API key** (recommended for service-to-service):

```java
.apiKey(System.getenv("RISK_API_KEY"))
```

The key is sent as `Authorization: Bearer <key>` on every request. Store it in your
secrets manager (AWS Secrets Manager, Vault, k8s Secret) — never in source code or JVM
system properties.

**OAuth2 client credentials** (for tenants with IdP integration):

```java
.oauth2(OAuth2Config.builder()
    .tokenEndpoint("https://auth.riskplatform.com/token")
    .clientId(System.getenv("OAUTH_CLIENT_ID"))
    .clientSecret(System.getenv("OAUTH_CLIENT_SECRET"))
    .build())
```

The SDK handles token refresh automatically before expiry.

## Communication channels

### 1. REST sync — `client.sync().evaluate(req)`

Synchronous point evaluation. Blocks until a `RiskDecision` is returned or the timeout
fires. Use for transactional flows where you need an immediate decision.

```java
RiskRequest req = new RiskRequest("txn-001", 1500.00, "user-42", "device-99");
RiskDecision decision = client.sync().evaluate(req);

// decision.outcome()     -> Outcome.APPROVE | DECLINE | REVIEW
// decision.score()       -> double 0.0–1.0
// decision.reason()      -> List<String>
// decision.traceId()     -> String (correlate with OTEL spans)
```

Expected latency: < 300 ms at p99 under normal load. Returns HTTP 200 on success.
Throws `RiskClientException` on 4xx/5xx (see Error handling).

### 2. REST batch — `client.sync().evaluateBatch(reqs)`

Evaluate a list of requests in a single HTTP call. The server processes them
sequentially and returns one decision per input in the same order.

```java
List<RiskRequest> requests = List.of(
    new RiskRequest("txn-001", 500.00, "user-10", "device-1"),
    new RiskRequest("txn-002", 9999.00, "user-11", "device-2")
);

List<RiskDecision> decisions = client.sync().evaluateBatch(requests);
decisions.forEach(d -> System.out.printf("%s -> %s%n", d.transactionId(), d.outcome()));
```

Prefer batch over N sequential calls when processing queued work. Max batch size: 100.

### 3. Server-Sent Events — `client.stream().decisions()`

Opens a persistent SSE connection and returns a `Stream<DecisionEvent>`. The server
pushes events as they are produced. The connection auto-reconnects on drop with
exponential back-off (configurable via `RetryPolicy`).

```java
// Consume indefinitely in a background thread
CompletableFuture.runAsync(() ->
    client.stream().decisions()
          .forEach(event -> processEvent(event))
);

// Consume a bounded window
client.stream().decisions()
      .limit(50)
      .forEach(this::processEvent);

// Close the stream programmatically
Stream<DecisionEvent> stream = client.stream().decisions();
// ... pass stream around, then:
stream.close(); // sends SSE close signal, releases connection
```

`DecisionEvent` carries: `eventId`, `transactionId`, `outcome`, `score`, `timestamp`.

### 4. WebSocket — `client.channel().open()`

Bidirectional channel. Send a request, receive the matching response asynchronously.
Suitable for interactive/low-latency flows. The SDK manages heartbeat/ping-pong
automatically; the connection is re-established on network failure.

```java
try (RiskChannel ch = client.channel().open()) {
    // Send a request
    CompletableFuture<Void> sent = ch.send(req);
    sent.join();

    // Receive the paired response (blocks up to 2 s)
    RiskDecision reply = ch.receive(Duration.ofSeconds(2));
    System.out.println(reply.outcome());

    // Async receive without blocking
    ch.receiveAsync().thenAccept(this::handleDecision);
}
// AutoCloseable — connection closed on exit from try-with-resources
```

### 5. Webhooks — `client.webhooks()`

Register a callback URL that the server calls when decisions match your filter.
Useful for event-driven architectures where you do not want to poll.

```java
// Subscribe
Subscription sub = client.webhooks().subscribe(
    "https://my-service.internal/risk-callback",
    "DECLINE,REVIEW"   // comma-separated filter: APPROVE | DECLINE | REVIEW
);
System.out.println(sub.subscriptionId()); // keep this for unsubscribe

// List active subscriptions
List<Subscription> active = client.webhooks().list();

// Unsubscribe
client.webhooks().unsubscribe(sub.subscriptionId());

// Verify HMAC signature on incoming callback (call from your HTTP handler)
boolean valid = client.webhooks().verify(
    requestBodyBytes,
    request.getHeader("X-Risk-Signature"),
    System.getenv("WEBHOOK_SECRET")
);
if (!valid) throw new SecurityException("Webhook signature mismatch");
```

The shared secret used for HMAC-SHA256 verification is provisioned when you subscribe.
Store it alongside your API key.

### 6. Kafka events — `client.events()`

Subscribe to the decision stream via Kafka. The SDK manages broker discovery, consumer
group coordination, offset commits, and deserialization. You provide a group ID and a
handler; the SDK handles the rest.

```java
// Consume decisions — runs until the JVM shuts down or cancel() is called
CancellationToken token = client.events().consumeDecisions(
    "my-consumer-group",
    (DecisionEvent event) -> {
        processDecision(event);
        // Ack is automatic after handler returns without throwing.
        // Throw to trigger nack and retry.
    }
);

// Publish a custom event into the pipeline
CustomEventEnvelope envelope = CustomEventEnvelope.builder()
    .eventType("FRAUD_SIGNAL")
    .payload(Map.of("userId", "user-42", "signal", "device_mismatch"))
    .build();
client.events().publishCustomEvent(envelope);

// Explicit ack if you need manual control
client.events().ackProcessed(event.eventId());

// Graceful shutdown
token.cancel();
```

### 7. SQS queue — `client.queue()`

Post requests to an SQS queue for asynchronous processing, or pull decision results
from the response queue. The SDK handles visibility timeouts and deletion after
successful processing.

```java
// Send a request to the inbound queue
client.queue().sendDecisionRequest(req);

// Receive and process decisions from the outbound queue
// Runs until cancelled. Deletes messages from queue after handler returns.
CancellationToken token = client.queue().receiveDecisions(
    (RiskDecision decision) -> {
        storeResult(decision);
        // Return normally -> message deleted from queue.
        // Throw -> message becomes visible again after visibility timeout.
    }
);

token.cancel(); // stop polling
```

### 8. Admin operations — `client.admin()`

Manage the rules engine at runtime. Requires an API key with `ADMIN` scope.

```java
// List all active rules
List<Rule> rules = client.admin().listRules();
rules.forEach(r -> System.out.printf("%s -> %s%n", r.id(), r.description()));

// Hot-reload rules from backing store without restart
ReloadResult result = client.admin().reloadRules();
System.out.println(result.rulesLoaded());

// Dry-run a single rule against a request
DryRunResult dryRun = client.admin().testRule("rule-max-amount", req);
System.out.println(dryRun.wouldTrigger());

// Audit trail — decisions and rule changes
List<AuditEntry> trail = client.admin().rulesAuditTrail();
```

## Error handling

All SDK operations throw `RiskClientException` (unchecked) on failure. Subclasses carry
structured error information.

```java
try {
    RiskDecision d = client.sync().evaluate(req);
} catch (RiskTimeoutException e) {
    // Request exceeded configured timeout. Retries exhausted.
    log.warn("Timeout after {} ms, traceId={}", e.elapsedMs(), e.traceId());
    // Fail open or route to fallback
} catch (RiskAuthException e) {
    // 401 — API key expired or revoked. Rotate the key.
    alertOncall("risk-api-key rotation required");
} catch (RiskSchemaException e) {
    // 422 — server rejected the request shape. Usually a client-side bug.
    log.error("Schema mismatch: {}", e.validationErrors());
} catch (RiskServerException e) {
    // 5xx — server-side error. Check server health.
    if (e.statusCode() == 503) fallbackToCache();
} catch (RiskUpgradeRequiredException e) {
    // 426 — server version is newer than SDK. Bump SDK version.
    log.error("SDK out of date. Server requires SDK >= {}", e.minSdkVersion());
} catch (RiskClientException e) {
    // Catch-all for other failures
    log.error("Unexpected risk client error", e);
}
```

## Retry and timeout

```java
// Exponential back-off (default)
RetryPolicy.exponentialBackoff()
    .maxAttempts(3)
    .initialDelay(Duration.ofMillis(100))
    .multiplier(2.0)
    .maxDelay(Duration.ofSeconds(2))
    .retryOn(RiskServerException.class)   // only retry 5xx, not 4xx

// Fixed delay
RetryPolicy.fixed(Duration.ofMillis(200)).maxAttempts(2)

// No retry (e.g. when caller owns retry logic)
RetryPolicy.none()
```

The `timeout` configured on the builder is a per-attempt budget. Total wall-clock time
is bounded by `timeout * maxAttempts + sum(delays)`. Integrate with Resilience4j or
Hystrix for circuit-breaker patterns by wrapping calls in their decorator before passing
to the SDK.

## Observability

The SDK propagates OpenTelemetry trace context automatically. Every outbound HTTP request
carries `traceparent` and `tracestate` headers derived from the active span.

```java
// Auto-configure from environment (OTEL_EXPORTER_OTLP_ENDPOINT, etc.)
.observability(OtelOptions.fromEnv())

// Explicit endpoint
.observability(OtelOptions.builder()
    .otlpEndpoint("http://otel-collector:4318")
    .serviceName("my-payment-service")
    .build())
```

Every `RiskDecision` carries a `traceId()` that matches the server-side span, allowing
end-to-end trace correlation in Jaeger or any OTLP-compatible backend.

## Versioning

This SDK follows [Semantic Versioning](https://semver.org/):

- **MAJOR** — breaking API change. Server runs both versions for 6 months minimum.
  Migration playbook published in `docs/migrations/`.
- **MINOR** — additive change (new method, optional field, new channel).
  Fully backward-compatible.
- **PATCH** — bug fix, performance improvement, dependency update. No API change.

When the server deprecates a feature it sends `Deprecation: true` and `Sunset: <date>`
response headers. The SDK logs a one-time warning per session and continues working.

## Compatibility matrix

| Server version | SDK 1.0 | SDK 1.1 | SDK 2.0 |
|---|---|---|---|
| Server 1.0 | compatible | compatible | not compatible |
| Server 1.1 | compatible (deprecated features unused) | compatible | not compatible |
| Server 2.0 | not compatible | compatible (with /v1 fallback) | compatible |

Full matrix: [docs/22-client-sdks.md](../../docs/22-client-sdks.md).

## Examples

### Sync evaluation with fallback

```java
RiskDecision decide(RiskRequest req) {
    try {
        return client.sync().evaluate(req);
    } catch (RiskTimeoutException | RiskServerException e) {
        log.warn("Risk engine unavailable, using fallback policy");
        return RiskDecision.fallback(req.transactionId());
    }
}
```

### Async batch with CompletableFuture

```java
CompletableFuture<List<RiskDecision>> future = CompletableFuture.supplyAsync(
    () -> client.sync().evaluateBatch(requests),
    executor
);
future.thenAccept(decisions -> decisions.forEach(this::record));
```

### Webhook integration

```java
// On startup
Subscription sub = client.webhooks().subscribe(callbackUrl, "DECLINE");
store.put("webhook-sub-id", sub.subscriptionId());

// In your HTTP handler (e.g. Spring @PostMapping)
@PostMapping("/risk-callback")
ResponseEntity<Void> onWebhook(@RequestBody byte[] body,
                               @RequestHeader("X-Risk-Signature") String sig) {
    if (!client.webhooks().verify(body, sig, webhookSecret)) {
        return ResponseEntity.status(401).build();
    }
    RiskDecision d = objectMapper.readValue(body, RiskDecision.class);
    processDecline(d);
    return ResponseEntity.ok().build();
}
```

### Kafka consumer with graceful shutdown

```java
CancellationToken token = client.events().consumeDecisions(
    "fraud-alert-group",
    event -> {
        if (event.outcome() == Outcome.DECLINE) alertFraudTeam(event);
    }
);
Runtime.getRuntime().addShutdownHook(new Thread(token::cancel));
```

### Full E2E: build, call, assert (JUnit 5 integration test)

```java
@Test
void evaluate_low_amount_returns_approve() {
    RiskClient client = RiskClient.builder()
        .environment(Environment.LOCAL)
        .apiKey(System.getenv("RISK_CLIENT_API_KEY"))
        .timeout(Duration.ofMillis(500))
        .build();

    RiskRequest req = new RiskRequest("txn-test-001", 100.00, "user-1", "device-1");
    RiskDecision decision = client.sync().evaluate(req);

    assertThat(decision.outcome()).isEqualTo(Outcome.APPROVE);
    assertThat(decision.traceId()).isNotBlank();
}
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `RiskTimeoutException` on first call | Server not reachable or wrong `Environment` | Run `client.sync().health()` to verify connectivity |
| `RiskAuthException` (401) | API key expired or wrong env key | Rotate key in secrets manager; verify `RISK_API_KEY` env var |
| `426 Upgrade Required` | Server version newer than SDK | Bump SDK to latest patch: `implementation("io.riskplatform.poc:risk-client:1.x.y")` |
| SSE stream disconnects immediately | Firewall/load balancer idle timeout | Set `timeout` >= LB idle timeout or configure SSE keep-alive on server |
| WebSocket `ChannelClosedException` | Heartbeat timeout — 30 s default | Ensure no proxy strips `Upgrade: websocket`; check reverse proxy WS config |
| `RiskSchemaException` (422) | Request field invalid or missing | Log `e.validationErrors()` and fix request shape |
| Kafka consumer stuck, no messages | Wrong consumer group or topic lag | Check consumer group offsets via Kafka CLI; verify broker address resolves |
| SQS `ReceiveDecisions` never fires handler | Queue empty or wrong region | Confirm queue ARN and region match the configured `Environment` |
| `RiskServerException` (503) | Server under load or restarting | Enable retry policy and circuit breaker; check server health endpoint |
| Admin calls return 403 | API key lacks ADMIN scope | Request ADMIN-scoped key from platform team |
| `Deprecation: true` warning in logs | Server feature deprecated | Plan migration; see `Sunset` date in header for deadline |
| Memory growth with SSE | Stream not closed after use | Always call `stream.close()` or use try-with-resources |

## Migrating from other clients

This is the v1 SDK. No migration from a previous SDK is required.

If you previously made raw HTTP calls to the risk engine, replace them:

```java
// Before (manual HTTP)
HttpResponse<String> resp = httpClient.send(
    HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/risk/evaluate"))
        .header("Authorization", "Bearer " + apiKey)
        .POST(BodyPublishers.ofString(json))
        .build(),
    BodyHandlers.ofString()
);

// After (SDK)
RiskDecision decision = client.sync().evaluate(req);
```

The SDK handles auth, retry, timeout, tracing, and schema evolution automatically.

## Contributing

Bug reports and feature requests: open a GitHub issue.

Development setup:

```bash
./gradlew :sdks:risk-client-java:build
./gradlew :sdks:risk-client-java:test
./gradlew :sdks:risk-client-java:integrationTest   # requires Docker
```

All PRs require passing unit tests and at least one new integration test for new channels.

## License

Apache-2.0

## Related docs

- [docs/22-client-sdks.md](../../docs/22-client-sdks.md) — Design rationale, SemVer policy, deprecation rules.
- [API specs](http://localhost:8080/openapi.json) — OpenAPI 3.1 (REST) + AsyncAPI 3.0 (events/WS/webhooks).
- [Cross-SDK contract tests](../contract-test/) — proves Java/TypeScript/Go agree on every channel.
- [sdks/README.md](../README.md) — Cross-language equivalence table.
