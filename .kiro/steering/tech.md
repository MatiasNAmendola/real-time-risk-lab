---
inclusion: always
---

# Tech Stack

Java 21 LTS baseline operativo, --release 21; Java 25 LTS es objetivo documentado.
Vert.x 5.0.12 reactive (non-blocking event loop).
Gradle 3.9, Postgres 16, Valkey 8, Redpanda v24.2.4.
OpenTelemetry Java agent 2.x, OpenObserve backend.
k3d/OrbStack, Helm 3, ArgoCD 9.2.4, Argo Rollouts 2.40.5.

Full versions: .ai/context/stack.md

## Non-negotiable

- Java 21 LTS baseline operativo; Java 25 LTS es objetivo documentado, no build actual.
- Every request must produce trace + log + metric via OTEL.
- correlationId in MDC and response header X-Correlation-Id.
