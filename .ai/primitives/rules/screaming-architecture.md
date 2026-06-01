---
name: screaming-architecture
applies_to: ["poc/**/src/**", "poc/**/README.md", "docs/**/*.md", "vault/**/*.md"]
priority: medium
---

# Rule: Screaming Architecture

## Principle

A service should reveal the business capability before the framework, runtime, or layer.

Prefer capability-first names such as `riskdecision`, `fraudrules`, `audit`, `mlscorer`, `transactional-risk`, `accounts`, `ledger`, or `payments`.
Technical names such as `controller`, `repository`, `usecase`, `infrastructure`, `nestjs`, `hono`, `vertx`, or `eventbus` may exist, but they should appear below a business capability or as PoC experiment names.

## Java packages

Use a business-capability prefix before topology details:

```text
io.riskplatform.riskdecision.cleanengine.domain
io.riskplatform.riskdecision.monolith.controller
io.riskplatform.riskdecision.layerpodhttp.usecase
io.riskplatform.riskdecision.layerpodeventbus.shared
io.riskplatform.servicemesh.fraudrules.domain
```

## TypeScript PoCs

Keep the business capability immediately below `internal/`, then Clean Architecture layers:

```text
src/internal/transactional-risk/domain
src/internal/transactional-risk/application
src/internal/transactional-risk/infrastructure
```

Aliases may keep `@domain`, `@application`, and `@infrastructure` for readability, but they must resolve under the business capability.

## Exceptions

PoC directory names may still describe the experiment or stack, for example `vertx-layer-as-pod-http` or `nestjs-distributed-transactions`, because the repository compares topologies and frameworks. The service source tree should still reveal the business capability first.
