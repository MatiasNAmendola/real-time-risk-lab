---
inclusion: always
---

# Tech Stack

Java 21 LTS executable baseline via Gradle toolchains (`--release 21`). Java 25 LTS is a documented future target.
Vert.x 5.0.12 reactive (non-blocking event loop).
Gradle 3.9, Postgres 16, Valkey 8, Redpanda v24.2.4.
OpenTelemetry Java agent 2.x, OpenObserve backend.
k3d/OrbStack, Helm 3, ArgoCD 9.2.4, Argo Rollouts 2.40.5.

Full versions: .ai/context/stack.md

## Non-negotiable

- Java 21 LTS is the executable baseline via Gradle toolchains. Java 25 LTS is a future target only.
- Every request must produce trace + log + metric via OTEL.
- correlationId in MDC and response header X-Correlation-Id.
