---
inclusion: always
---

# Tech Stack

Java 25 LTS, --release 25, virtual threads for blocking I/O.
Vert.x 5.0.12 reactive (non-blocking event loop).
Maven 3.9, Postgres 16, Valkey 8, Redpanda v24.2.4.
OpenTelemetry Java agent 2.x, OpenObserve backend.
k3d/OrbStack, Helm 3, ArgoCD 9.2.4, Argo Rollouts 2.40.5.

Full versions: .ai/context/stack.md

## Non-negotiable

- Java 25 LTS only. Do NOT downgrade to 21 or upgrade to 26.
- Every request must produce trace + log + metric via OTEL.
- correlationId in MDC and response header X-Correlation-Id.
