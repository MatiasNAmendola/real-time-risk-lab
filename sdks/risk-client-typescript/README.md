# @riskplatform/risk-client

> Risk engine client SDK for the TypeScript/Node.js ecosystem. Encapsulates 7 communication
> channels (REST sync, REST batch, SSE, WebSocket, Webhooks, Kafka, SQS, Admin).
> Production-ready, semver-compatible, infrastructure-agnostic.

## Install

```bash
npm install @riskplatform/risk-client
```

Requires Node.js 18+ and TypeScript 5+.

## Quick start

```typescript
import { RiskClient, Environment } from '@riskplatform/risk-client';

const client = new RiskClient({
  environment: Environment.LOCAL,
  apiKey: process.env.RISK_API_KEY!,
  timeoutMs: 280,
  retry: { strategy: 'exponential', maxAttempts: 3 },
});

const req = { transactionId: 'txn-001', amount: 1500.00, userId: 'user-42', deviceId: 'device-99' };
const decision = await client.sync.evaluate(req);
console.log(decision.outcome); // 'APPROVE' | 'DECLINE' | 'REVIEW'
```

## Configuration

### Environments

| Value | REST base URL | Use for |
|---|---|---|
| `Environment.PROD` | `https://risk.example.com` | Production traffic |
| `Environment.STAGING` | `https://risk-staging.riskplatform.com` | Pre-prod validation |
| `Environment.DEV` | `https://risk-dev.riskplatform.com` | Feature branches |
| `Environment.LOCAL` | `http://localhost:8080` | Local development |

The SDK resolves all downstream URLs (REST endpoints, Kafka brokers, SQS queue ARNs,
WebSocket paths) from the `environment` value. You never hard-code infrastructure URLs.

### Full config options

```typescript
import { RiskClient, Environment, RetryStrategy } from '@riskplatform/risk-client';

const client = new RiskClient({
  environment: Environment.PROD,
  apiKey: process.env.RISK_API_KEY!,        // required
  timeoutMs: 280,                            // per-request timeout in ms
  retry: {
    strategy: RetryStrategy.EXPONENTIAL,
    maxAttempts: 3,
    initialDelayMs: 100,
    maxDelayMs: 2000,
  },
  observability: {
    otlpEndpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT,
    serviceName: 'my-payment-service',
  },
  endpointOverride: 'http://localhost:8080', // optional: bypass environment resolution
});
```

### Authentication

The SDK supports two auth modes:

**API key** (recommended for service-to-service):

```typescript
apiKey: process.env.RISK_API_KEY!
```

The key is sent as `Authorization: Bearer <key>` on every request. Store it in your
secrets manager (AWS Secrets Manager, Vault, k8s Secret, `.env` loaded via `dotenv`) —
never commit it to source code.

**OAuth2 client credentials** (for tenants with IdP integration):

```typescript
oauth2: {
  tokenEndpoint: 'https://auth.riskplatform.com/token',
  clientId: process.env.OAUTH_CLIENT_ID!,
  clientSecret: process.env.OAUTH_CLIENT_SECRET!,
}
```

The SDK handles token refresh automatically before expiry.

## Communication channels

### 1. REST sync — `client.sync.evaluate(req)`

Synchronous point evaluation. Returns a `Promise<RiskDecision>` that resolves when
the server responds or rejects on timeout/error. Use for transactional flows.

```typescript
import { RiskRequest, RiskDecision, Outcome } from '@riskplatform/risk-client';

const req: RiskRequest = {
  transactionId: 'txn-001',
  amount: 1500.00,
  userId: 'user-42',
  deviceId: 'device-99',
};

const decision: RiskDecision = await client.sync.evaluate(req);
// decision.outcome    -> 'APPROVE' | 'DECLINE' | 'REVIEW'
// decision.score      -> number 0.0–1.0
// decision.reason     -> string[]
// decision.traceId    -> string (correlate with OTEL spans)
```

Expected latency: < 300 ms at p99. Throws `RiskClientError` on 4xx/5xx (see Error handling).

### 2. REST batch — `client.sync.evaluateBatch(reqs)`

Evaluate an array of requests in a single HTTP call. Returns one decision per input
in the same order.

```typescript
const requests: RiskRequest[] = [
  { transactionId: 'txn-001', amount: 500.00, userId: 'user-10', deviceId: 'dev-1' },
  { transactionId: 'txn-002', amount: 9999.00, userId: 'user-11', deviceId: 'dev-2' },
];

const decisions: RiskDecision[] = await client.sync.evaluateBatch(requests);
decisions.forEach(d => console.log(`${d.transactionId} -> ${d.outcome}`));
```

Prefer batch over N sequential awaits when processing queued work. Max batch size: 100.

### 3. Server-Sent Events — `client.stream.decisions()`

Returns an `AsyncIterableIterator<DecisionEvent>`. Use `for await...of` to consume.
The connection auto-reconnects on drop with exponential back-off.

```typescript
// Consume indefinitely
async function consumeStream(): Promise<void> {
  for await (const event of client.stream.decisions()) {
    console.log(event.transactionId, event.outcome);
  }
}

// Bounded consumption — stop after N events
let count = 0;
for await (const event of client.stream.decisions()) {
  processEvent(event);
  if (++count >= 50) break; // iterator is cleaned up automatically on break
}

// With AbortController for programmatic cancellation
const ac = new AbortController();
setTimeout(() => ac.abort(), 30_000);

for await (const event of client.stream.decisions({ signal: ac.signal })) {
  processEvent(event);
}
```

`DecisionEvent` carries: `eventId`, `transactionId`, `outcome`, `score`, `timestamp`.

### 4. WebSocket — `client.channel.open()`

Bidirectional channel. Send requests and receive responses asynchronously. Suitable
for interactive/low-latency flows. The SDK manages heartbeat/ping-pong automatically.

```typescript
import { RiskChannel } from '@riskplatform/risk-client';

const ch: RiskChannel = await client.channel.open();

try {
  await ch.send(req);

  // Wait for paired response (rejects after timeoutMs)
  const reply: RiskDecision = await ch.receive(2000);
  console.log(reply.outcome);

  // Or listen for all incoming messages
  ch.onMessage((decision: RiskDecision) => {
    handleDecision(decision);
  });

  ch.onError((err: Error) => console.error('WS error', err));
} finally {
  ch.close();
}
```

### 5. Webhooks — `client.webhooks`

Register a callback URL that the server calls when decisions match your filter.
Useful for event-driven architectures where you do not want to poll.

```typescript
import { Subscription } from '@riskplatform/risk-client';
import { createHmac } from 'crypto';

// Subscribe
const sub: Subscription = await client.webhooks.subscribe(
  'https://my-service.internal/risk-callback',
  'DECLINE,REVIEW'   // comma-separated filter: APPROVE | DECLINE | REVIEW
);
console.log(sub.subscriptionId);

// List active subscriptions
const active: Subscription[] = await client.webhooks.list();

// Unsubscribe
await client.webhooks.unsubscribe(sub.subscriptionId);

// Verify HMAC signature on incoming callback (call from your HTTP handler)
// Express example:
app.post('/risk-callback', express.raw({ type: '*/*' }), (req, res) => {
  const valid = client.webhooks.verify(
    req.body,                                // raw Buffer
    req.headers['x-risk-signature'] as string,
    process.env.WEBHOOK_SECRET!
  );
  if (!valid) return res.status(401).send('invalid signature');

  const decision: RiskDecision = JSON.parse(req.body.toString());
  processDecline(decision);
  res.status(200).end();
});
```

### 6. Kafka events — `client.events`

Subscribe to the decision stream via Kafka. The SDK manages broker discovery,
consumer group coordination, offset commits, and deserialization.

```typescript
import { DecisionEvent, CancellationToken } from '@riskplatform/risk-client';

// Consume decisions
const token: CancellationToken = await client.events.consumeDecisions(
  'my-consumer-group',
  async (event: DecisionEvent) => {
    await processDecision(event);
    // Return normally -> ack.  Throw -> nack, message will be retried.
  }
);

// Publish a custom event into the pipeline
await client.events.publishCustomEvent({
  eventType: 'FRAUD_SIGNAL',
  payload: { userId: 'user-42', signal: 'device_mismatch' },
});

// Explicit ack if you need manual control
await client.events.ackProcessed(event.eventId);

// Graceful shutdown
await token.cancel();
```

### 7. SQS queue — `client.queue`

Post requests to an SQS queue for asynchronous processing, or pull decision results
from the response queue. The SDK manages visibility timeouts and deletion.

```typescript
import { CancellationToken } from '@riskplatform/risk-client';

// Send a request to the inbound queue
await client.queue.sendDecisionRequest(req);

// Receive and process decisions from the outbound queue
const token: CancellationToken = await client.queue.receiveDecisions(
  async (decision: RiskDecision) => {
    await storeResult(decision);
    // Return normally -> message deleted.  Throw -> becomes visible again.
  }
);

// Stop polling
await token.cancel();
```

### 8. Admin operations — `client.admin`

Manage the rules engine at runtime. Requires an API key with `ADMIN` scope.

```typescript
import { Rule, ReloadResult, DryRunResult, AuditEntry } from '@riskplatform/risk-client';

// List all active rules
const rules: Rule[] = await client.admin.listRules();
rules.forEach(r => console.log(`${r.id} -> ${r.description}`));

// Hot-reload rules from backing store without restart
const result: ReloadResult = await client.admin.reloadRules();
console.log(result.rulesLoaded);

// Dry-run a single rule against a request
const dryRun: DryRunResult = await client.admin.testRule('rule-max-amount', req);
console.log(dryRun.wouldTrigger);

// Audit trail
const trail: AuditEntry[] = await client.admin.rulesAuditTrail();
```

## Error handling

All SDK async methods reject with a `RiskClientError` subclass on failure.

```typescript
import {
  RiskTimeoutError,
  RiskAuthError,
  RiskSchemaError,
  RiskServerError,
  RiskUpgradeRequiredError,
  RiskClientError,
} from '@riskplatform/risk-client';

try {
  const decision = await client.sync.evaluate(req);
} catch (err) {
  if (err instanceof RiskTimeoutError) {
    // Request exceeded configured timeout. Retries exhausted.
    console.warn(`Timeout after ${err.elapsedMs}ms, traceId=${err.traceId}`);
    return fallbackDecision(req.transactionId);
  }
  if (err instanceof RiskAuthError) {
    // 401 — API key expired or revoked.
    alertOncall('risk-api-key rotation required');
  }
  if (err instanceof RiskSchemaError) {
    // 422 — request rejected by server validation.
    console.error('Schema mismatch:', err.validationErrors);
  }
  if (err instanceof RiskServerError) {
    // 5xx — server-side error.
    if (err.statusCode === 503) return fallbackToCache();
  }
  if (err instanceof RiskUpgradeRequiredError) {
    // 426 — server version is newer than SDK.
    console.error(`SDK out of date. Server requires SDK >= ${err.minSdkVersion}`);
  }
  if (err instanceof RiskClientError) {
    // Catch-all
    console.error('Unexpected risk client error', err);
  }
}
```

## Retry and timeout

```typescript
// Exponential back-off (default)
retry: {
  strategy: 'exponential',
  maxAttempts: 3,
  initialDelayMs: 100,
  multiplier: 2,
  maxDelayMs: 2000,
  retryOn: ['RiskServerError'],  // only retry 5xx, not 4xx
}

// Fixed delay
retry: { strategy: 'fixed', delayMs: 200, maxAttempts: 2 }

// No retry (caller owns retry logic)
retry: { strategy: 'none' }
```

`timeoutMs` is a per-attempt budget. Integrate with `opossum` (circuit breaker) by
wrapping SDK calls in a `CircuitBreaker.fire()` call.

## Observability

The SDK propagates OpenTelemetry trace context automatically. Every outbound request
carries `traceparent` and `tracestate` headers from the active span.

```typescript
observability: {
  otlpEndpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT, // e.g. http://otel-collector:4318
  serviceName: 'my-payment-service',
}
```

If `@opentelemetry/sdk-node` is already initialized in your process, the SDK inherits
the global tracer provider automatically — no additional config needed.

Every `RiskDecision` carries a `traceId` that matches the server-side span for
end-to-end trace correlation.

## Versioning

This SDK follows [Semantic Versioning](https://semver.org/):

- **MAJOR** — breaking API change. Server runs both versions for 6 months minimum.
- **MINOR** — additive change (new method, optional field, new channel).
- **PATCH** — bug fix, performance improvement, dependency update.

When the server deprecates a feature it sends `Deprecation: true` and `Sunset: <date>`
response headers. The SDK logs a one-time warning per process and continues working.

## Compatibility matrix

| Server version | SDK 1.0 | SDK 1.1 | SDK 2.0 |
|---|---|---|---|
| Server 1.0 | compatible | compatible | not compatible |
| Server 1.1 | compatible (deprecated features unused) | compatible | not compatible |
| Server 2.0 | not compatible | compatible (with /v1 fallback) | compatible |

Full matrix: [vault/04-Concepts/Client-SDK-Strategy.md](../../vault/04-Concepts/Client-SDK-Strategy.md).

## Examples

### Sync evaluation with fallback

```typescript
async function decide(req: RiskRequest): Promise<RiskDecision> {
  try {
    return await client.sync.evaluate(req);
  } catch (err) {
    if (err instanceof RiskTimeoutError || err instanceof RiskServerError) {
      console.warn('Risk engine unavailable, using fallback policy');
      return { transactionId: req.transactionId, outcome: 'APPROVE', score: 0, reason: ['fallback'], traceId: '' };
    }
    throw err;
  }
}
```

### Async batch processing

```typescript
async function processBatch(reqs: RiskRequest[]): Promise<void> {
  const decisions = await client.sync.evaluateBatch(reqs);
  await Promise.all(decisions.map(d => recordDecision(d)));
}
```

### Webhook integration (Express)

```typescript
app.post('/risk-callback', express.raw({ type: '*/*' }), (req, res) => {
  if (!client.webhooks.verify(req.body, req.headers['x-risk-signature'] as string, secret)) {
    return res.status(401).end();
  }
  const d: RiskDecision = JSON.parse(req.body.toString());
  if (d.outcome === 'DECLINE') triggerFraudAlert(d);
  res.status(200).end();
});
```

### Kafka consumer with graceful shutdown

```typescript
const token = await client.events.consumeDecisions('fraud-alert-group', async (event) => {
  if (event.outcome === 'DECLINE') await alertFraudTeam(event);
});

process.on('SIGTERM', async () => {
  await token.cancel();
  process.exit(0);
});
```

### Full E2E: Jest integration test

```typescript
import { RiskClient, Environment, Outcome } from '@riskplatform/risk-client';

describe('RiskClient integration', () => {
  let client: RiskClient;

  beforeAll(() => {
    client = new RiskClient({ environment: Environment.LOCAL, apiKey: process.env.RISK_CLIENT_API_KEY ?? 'change-me-client-api-key', timeoutMs: 500 });
  });

  it('evaluate low amount returns APPROVE', async () => {
    const req = { transactionId: 'txn-test-001', amount: 100.00, userId: 'user-1', deviceId: 'dev-1' };
    const decision = await client.sync.evaluate(req);
    expect(decision.outcome).toBe(Outcome.APPROVE);
    expect(decision.traceId).toBeTruthy();
  });
});
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `RiskTimeoutError` on first call | Server not reachable or wrong `environment` | Run `await client.sync.health()` to verify connectivity |
| `RiskAuthError` (401) | API key expired or wrong env key | Rotate key in secrets manager; check `RISK_API_KEY` env var |
| `426 Upgrade Required` | Server version newer than SDK | Run `npm install @riskplatform/risk-client@latest` |
| SSE stream never yields | Firewall/proxy strips `text/event-stream` | Ensure proxy passes `Accept: text/event-stream` and does not buffer |
| `for await...of` exits immediately | `AbortController` already aborted | Check `signal.aborted` before starting the loop |
| `RiskSchemaError` (422) | Request field invalid or missing | Log `err.validationErrors` and fix request shape against the type definition |
| Kafka consumer does not receive messages | Wrong consumer group or topic lag | Check offsets; confirm broker address resolves in `Environment` |
| SQS `receiveDecisions` handler never fires | Queue empty or wrong region | Confirm queue ARN and region match the configured `Environment` |
| `RiskServerError` (503) | Server under load or restarting | Enable retry policy; add circuit breaker via `opossum` |
| Admin calls return 403 | API key lacks ADMIN scope | Request ADMIN-scoped key from platform team |
| `Deprecation: true` warning in logs | Server feature deprecated | Plan migration; see `Sunset` date in header for deadline |
| WebSocket drops every 30 s | Proxy idle-connection timeout | Configure proxy keep-alive >= WS heartbeat interval (30 s) |

## Migrating from other clients

This is the v1 SDK. No migration from a previous SDK is required.

If you previously made raw `fetch` calls to the risk engine, replace them:

```typescript
// Before (manual fetch)
const resp = await fetch('http://localhost:8080/risk/evaluate', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
  body: JSON.stringify(req),
});
const decision = await resp.json();

// After (SDK)
const decision = await client.sync.evaluate(req);
```

The SDK handles auth, retry, timeout, tracing, and schema evolution automatically.

## Contributing

Bug reports and feature requests: open a GitHub issue.

Development setup:

```bash
cd sdks/risk-client-typescript
npm install
npm test
npm run test:integration   # requires Docker
npm run lint
npm run build
```

All PRs require passing unit tests (Jest) and at least one new integration test for
new channels.

## License

Apache-2.0

## Related docs

- [vault/04-Concepts/Client-SDK-Strategy.md](../../vault/04-Concepts/Client-SDK-Strategy.md) — Design rationale, SemVer policy, deprecation rules.
- [API specs](http://localhost:8080/openapi.json) — OpenAPI 3.1 (REST) + AsyncAPI 3.0 (events/WS/webhooks).
- [Cross-SDK contract tests](../contract-test/) — proves Java/TypeScript/Go agree on every channel.
- [sdks/README.md](../README.md) — Cross-language equivalence table.
