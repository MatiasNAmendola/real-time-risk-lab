# sdks/ — External-Facing SDK Modules

SDK modules expose stable, versioned contracts for use by consumers outside this monorepo
(e.g. other services, CLI tools, integration tests).

## Modules

| Module | Coordinates | Language | Description |
|---|---|---|---|
| `sdks:risk-events` | `io.riskplatform.poc:risk-events:0.1.0-SNAPSHOT` | Java 21 | Shared records: `RiskRequest`, `RiskDecision`, `DecisionEvent` |
| `sdks:risk-client-java` | `io.riskplatform.poc:risk-client:1.0.0-SNAPSHOT` | Java 21 | Full client SDK — REST, SSE, WS, Kafka, SQS, Webhooks, Admin |
| `sdks/risk-client-typescript` | `@riskplatform/risk-client@1.0.0` | TypeScript (Node 18+) | Full client SDK — same facade as Java |
| `sdks/risk-client-go` | `github.com/riskplatform/risk-client@v1.0.0` | Go 1.21+ | Full client SDK — same facade as Java |

## Install

### Java

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.riskplatform.poc:risk-client:1.0.0-SNAPSHOT")
}
```

### TypeScript

```bash
npm install @riskplatform/risk-client
```

### Go

```bash
go get github.com/riskplatform/risk-client@v1.0.0
```

## Client Facade — All 7 Channels

Each SDK exposes the same logical facade adapted to language idioms. The table below
shows every operation across all three languages.

### REST sync

| Java | TypeScript | Go |
|---|---|---|
| `client.sync().evaluate(req)` | `client.sync.evaluate(req)` | `client.Sync.Evaluate(ctx, req)` |
| `client.sync().evaluateBatch(reqs)` | `client.sync.evaluateBatch(reqs)` | `client.Sync.EvaluateBatch(ctx, reqs)` |
| `client.sync().health()` | `client.sync.health()` | `client.Sync.Health(ctx)` |

### Server-Sent Events (SSE)

| Java | TypeScript | Go |
|---|---|---|
| `client.stream().decisions()` returns `Stream<DecisionEvent>` | `client.stream.decisions()` returns `AsyncIterableIterator<DecisionEvent>` | `client.Stream.Decisions(ctx, handler)` — handler-based |

### WebSocket

| Java | TypeScript | Go |
|---|---|---|
| `client.channel().open()` returns `RiskChannel` (AutoCloseable) | `client.channel.open()` returns `RiskChannel` | `client.Channel.Open(ctx)` returns `RiskChannel` (io.Closer) |
| `ch.send(req).join()` | `await ch.send(req)` | `ch.Send(ctx, req)` |
| `ch.receive(timeoutMs)` | `await ch.receive(timeoutMs)` | `ch.Receive(ctx)` |
| `ch.close()` | `ch.close()` | `ch.Close()` |

### Kafka events

| Java | TypeScript | Go |
|---|---|---|
| `client.events().consumeDecisions(groupId, handler)` | `client.events.consumeDecisions(groupId, handler)` | `client.Events.ConsumeDecisions(ctx, groupId, handler)` |
| `client.events().publishCustomEvent(envelope)` | `client.events.publishCustomEvent(envelope)` | `client.Events.PublishCustomEvent(ctx, envelope)` |
| `client.events().ackProcessed(eventId)` | `client.events.ackProcessed(eventId)` | `client.Events.AckProcessed(ctx, eventId)` |

### SQS queue

| Java | TypeScript | Go |
|---|---|---|
| `client.queue().sendDecisionRequest(req)` | `client.queue.sendDecisionRequest(req)` | `client.Queue.SendDecisionRequest(ctx, req)` |
| `client.queue().receiveDecisions(handler)` | `client.queue.receiveDecisions(handler)` | `client.Queue.ReceiveDecisions(ctx, handler)` |
| `client.queue().deleteFromQueue(receiptHandle)` | `client.queue.deleteFromQueue(receiptHandle)` | `client.Queue.DeleteFromQueue(ctx, receiptHandle)` |

### Webhooks

| Java | TypeScript | Go |
|---|---|---|
| `client.webhooks().subscribe(url, filter)` | `client.webhooks.subscribe(url, filter)` | `client.Webhooks.Subscribe(ctx, url, filter)` |
| `client.webhooks().list()` | `client.webhooks.list()` | `client.Webhooks.List(ctx)` |
| `client.webhooks().unsubscribe(subId)` | `client.webhooks.unsubscribe(subId)` | `client.Webhooks.Unsubscribe(ctx, subId)` |
| `client.webhooks().verify(payload, sig, secret)` | `client.webhooks.verify(payload, sig, secret)` | `client.Webhooks.Verify(payload, sig, secret)` |

### Admin

| Java | TypeScript | Go |
|---|---|---|
| `client.admin().listRules()` | `client.admin.listRules()` | `client.Admin.ListRules(ctx)` |
| `client.admin().reloadRules()` | `client.admin.reloadRules()` | `client.Admin.ReloadRules(ctx)` |
| `client.admin().testRule(ruleId, req)` | `client.admin.testRule(ruleId, req)` | `client.Admin.TestRule(ctx, ruleId, req)` |
| `client.admin().rulesAuditTrail()` | `client.admin.rulesAuditTrail()` | `client.Admin.RulesAuditTrail(ctx)` |

## Idiom summary

| Feature | Java | TypeScript | Go |
|---|---|---|---|
| Async model | `CompletableFuture` | `async/await` + `Promise` | Blocking with `context.Context` cancellation |
| SSE iteration | `Stream<T>.forEach` | `for await...of AsyncIterableIterator` | Handler callback |
| WS resource management | `try-with-resources` (AutoCloseable) | `try/finally` with `ch.close()` | `defer ch.Close()` |
| Error type | Unchecked `RiskClientException` subclasses | `RiskClientError` subclasses | Typed error structs, `errors.As` |
| Cancellation | `CancellationToken.cancel()` | `CancellationToken.cancel()` | `context.CancelFunc` |
| Config style | Builder pattern | Plain object literal | Config struct literal |

## Compatibility matrix

| Server version | Java 1.0 | TS 1.0 | Go v1.0 |
|---|---|---|---|
| Server 1.0 | compatible | compatible | compatible |
| Server 1.1 | compatible | compatible | compatible |
| Server 2.0 | not compatible | not compatible | not compatible |

Full versioning policy and compatibility rules: [docs/22-client-sdks.md](../docs/22-client-sdks.md).

## Stability contract

- SDKs maintain backwards compatibility within a minor version.
- Breaking changes require a new major version coordinated with consumers.
- No internal `pkg:*` types may leak through SDK public API surfaces.
- When the server deprecates a feature it sends `Deprecation: true` + `Sunset: <date>` headers.
  All SDKs log a one-time warning per session/process and continue working until the sunset date.

## Full design

See [docs/22-client-sdks.md](../docs/22-client-sdks.md) for design rationale, SemVer policy,
deprecation rules, and migration playbook templates.

## Contract tests

[contract-test/](contract-test/) proves that all three SDKs produce identical decisions for
the same inputs, exercising every channel. Run with:

```bash
./gradlew :sdks:contract-test:test -Pcontract
```
