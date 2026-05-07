# vertx-risk-platform

Fourth PoC in the Risk Decision Platform portfolio. Same risk evaluation domain as the other three, different architecture.

## What this demonstrates

**HTTP inter-pod communication + token-based permissions.**

Three Vert.x verticles deployed as independent JVMs (pods), communicating via plain HTTP:

```
client -> controller (8080) -> usecase (8081) -> repository (8082)
                   x-pod-token: controller-risk-evaluate-token
                                           x-pod-token: usecase-repository-rw-token
```

The controller owns only the controller-to-usecase token. It cannot call the repository directly — the repository rejects any token that is not the usecase token. This is the HTTP+token model of least privilege.

### What the debug endpoint shows

`GET /debug/try-repository` on the controller deliberately calls the repository with the wrong token and returns the 403. This is the live proof of the permission boundary.

## Difference from vertx-distributed

| Dimension | vertx-risk-platform | java-vertx-distributed |
|---|---|---|
| Inter-pod comm | HTTP (visible, debuggable) | Hazelcast event bus (binary TCP) |
| Permission model | x-pod-token headers | Network-level + AWS credentials |
| Cluster manager | None | Hazelcast |
| Persistence | In-memory (PoC) | Postgres + Valkey |
| Host ports | 8180/8181/8182 | 8080/8081/8082 |
| Complexity | Lower (understand in 30 min) | Higher (realistic prod topology) |

**When to use HTTP+tokens in production:** microservices on a shared network where network segmentation is unavailable or too costly to maintain; teams that need auditability of every inter-service call; migration path from monolith where you want explicit permission grants visible in code rather than infra config.

**When to prefer event bus / network isolation:** high-throughput async pipelines where HTTP overhead is measurable; environments where network policy is enforced at the platform level (Kubernetes NetworkPolicy, AWS Security Groups); when you need guaranteed ordering or fan-out across consumers.

## How to run

### Local (Gradle, in-process)

```bash
# Build fat-jar
./gradlew :poc:vertx-risk-platform:shadowJar

# Start all 3 pods (background processes, local ports 8080/8081/8082)
./nx run vertx-platform

# Run unit + integration tests (no ports required)
./nx test --group vertx-platform
```

### Docker Compose

```bash
# Build + bring up (ports 8180/8181/8182 on host to avoid clash with vertx-distributed)
./nx up vertx-platform

# Validate the 3 pods
curl http://localhost:8180/health   # controller-pod
curl http://localhost:8181/health   # usecase-pod
curl http://localhost:8182/health   # repository-pod

# Full evaluate
curl -s -X POST http://localhost:8180/risk/evaluate \
  -H 'content-type: application/json' \
  -d '{"transactionId":"tx-1","customerId":"c-1","amountInCents":1000,"newDevice":false,"correlationId":"c-1","idempotencyKey":"ik-1"}'

# Show the permission boundary (controller trying to reach repository directly -> 403)
curl http://localhost:8180/debug/try-repository
```

### ATDD (requires pods running)

```bash
./nx test --group atdd-vertx-platform
# or
./gradlew :poc:vertx-risk-platform:atdd-tests:test -Patdd
```

## Architecture decision: why no event bus

The event bus (Hazelcast TCP) is powerful but opaque — you cannot curl it, you cannot see the message in a network trace, and the permission model lives in Hazelcast config rather than application code.

HTTP+tokens trades throughput for transparency:

- Every inter-pod call is a normal HTTP request visible in OTel traces, curl-able in dev, debuggable with Wireshark.
- Permission grants are in `PodSecurity.java` — reviewed in PRs, audited in git log.
- No cluster manager to configure, no port 5701 to open.

The cost: 1-2 ms of extra HTTP overhead per hop vs. the event bus. For a fraud decision system with a 300 ms p99 budget, this is acceptable. For a 5 ms p99 budget, prefer the monolith or bare-javac.

## Port allocation

| Service | Container port | Host port (compose) | Notes |
|---|---|---|---|
| controller-pod | 8080 | 8180 | avoids clash with java-vertx-distributed |
| usecase-pod | 8081 | 8181 | |
| repository-pod | 8082 | 8182 | |

UIDs: controller=1101, usecase=1102, repository=1103 (1001-1004 reserved for java-vertx-distributed).
