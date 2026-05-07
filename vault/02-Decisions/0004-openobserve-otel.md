---
adr: "0004"
title: OpenObserve as OTEL Backend
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/observability, area/otel]
---

# ADR-0004: OpenObserve as Unified OTEL Backend in k3d

## Estado

Aceptado el 2026-05-07.

## Contexto

The k3d local Kubernetes stack (`poc/k8s-local/`) requires an OTEL backend that accepts traces, metrics, and logs for end-to-end observability demonstration. The backend must: accept OTLP gRPC and HTTP, provide a UI for trace/log/metric correlation, run within k3d's resource constraints (< 500MB RAM total for the observability stack), and be locally accessible without internet.

SigNoz was evaluated but requires Kafka and ClickHouse (heavy). The production standard is typically Grafana stack (Tempo + Loki + Grafana + Prometheus), but that is 3-4 separate services. Other unified options were ruled out for footprint or operational complexity reasons.

## Decisión

Use OpenObserve (self-hosted, `openobserve/openobserve:latest`) as the unified OTEL backend in k3d. It accepts OTLP gRPC/HTTP for traces, metrics, and logs in a single binary with a built-in UI. The k3d stack also includes kube-prom-stack (Prometheus + Grafana) for Kubernetes infrastructure metrics — OpenObserve handles application-level traces and logs specifically.

## Alternativas consideradas

### Opción A: OpenObserve (single binary, unified) (elegida)
- **Ventajas**: Single binary accepts traces, metrics, and logs via OTLP; built-in UI for trace/log/metric correlation without separate Grafana; < 100MB RAM footprint; single Helm chart deployment; OTLP receiver compatible with the Vert.x OTel Java agent output; no separate Kafka or ClickHouse dependency.
- **Desventajas**: Not the production standard — less established in production teams; less ecosystem tooling than Grafana stack; community support smaller than Grafana OSS; not used at most large-scale organizations at this transactional throughput.
- **Por qué se eligió**: The footprint and simplicity constraints are hard — k3d on a MacBook Pro has memory limits. OpenObserve's single-binary model fits. The production answer ("in production we'd use Grafana Tempo + Loki + Prometheus") is a distinct, answerable design question.

### Opción B: Grafana Tempo + Loki + Grafana + Prometheus (production stack)
- **Ventajas**: Industry standard; engineers recognize it; Tempo handles traces, Loki handles logs, Prometheus handles metrics, Grafana unifies the UI; maximum production fidelity.
- **Desventajas**: 4 separate services + Grafana agents (Alloy/Agent) = ~8 containers; Loki requires a storage backend; Tempo requires S3 or object storage for production; total memory for all services exceeds k3d local budget; complex configuration (Grafana datasources, Loki HTTP endpoint, Tempo gRPC endpoint).
- **Por qué no**: The operational overhead on a local MacBook Pro k3d cluster is prohibitive. The kube-prom-stack already provides Prometheus + Grafana for infra metrics; adding Tempo + Loki on top would require 2-3GB additional RAM.

### Opción C: Jaeger (traces only)
- **Ventajas**: Well-known; easy setup; Jaeger UI is clear for trace visualization; standard in service mesh tutorials.
- **Desventajas**: Traces only — no metrics, no logs; correlating a trace with its log line requires jumping to a separate tool; does not demonstrate the "unified observability" narrative.
- **Por qué no**: The design signal requires showing trace/log/metric correlation — the 3 pillars of observability. Jaeger covers only one.

### Opción D: SigNoz (open-source Datadog alternative)
- **Ventajas**: Unified traces + metrics + logs; good UI; active community; closer to production observability platform than OpenObserve.
- **Desventajas**: Requires ClickHouse as a backend (heavy, ~1GB RAM); requires Kafka for the data pipeline; total resource footprint ~2-3GB for the full SigNoz stack; incompatible with a constrained k3d environment.
- **Por qué no**: ClickHouse + Kafka dependency makes SigNoz too heavy for local k3d. The resource requirements exceed OpenObserve by 10x.

### Opción E: Axiom (SaaS)
- **Ventajas**: Zero infrastructure; excellent UI; used in production SaaS contexts.
- **Desventajas**: Requires internet; requires API key; breaks in offline environments; SaaS data residency concerns; cannot be demoed without internet.
- **Por qué no**: Local-first constraint is hard. The demo must work offline.

## Consecuencias

### Positivo
- Single OTEL collector configuration — all three signal types (traces, metrics, logs) route to one endpoint.
- Trace/log/metric correlation works in the OpenObserve UI without cross-service linking.
- Low memory footprint allows the full Vert.x + Redpanda + Postgres + OpenObserve stack to run on a MacBook Pro 16GB.

### Negativo
- Engineers familiar with Grafana stack may not recognize OpenObserve — brief introduction needed.
- OpenObserve's query language (SQL-like) differs from Grafana's PromQL/LogQL — team onboarding requires relearning.

### Mitigaciones
- Stock answer: "In production I'd use Grafana Tempo + Loki. Locally I use OpenObserve because it fits in k3d's memory budget — same OTLP wire protocol, different backend."
- kube-prom-stack (included in k3d stack) provides Prometheus + Grafana for infra metrics — Grafana is familiar.

## Validación

- `kubectl port-forward svc/openobserve 5080:5080 -n monitoring` and navigate to `localhost:5080` shows traces from the Vert.x stack.
- OTEL Java agent on `controller-app` produces trace spans visible in OpenObserve with correct `traceparent` propagation.
- `DecisionEvaluated` Kafka event headers contain `traceparent` — end-to-end trace spans HTTP → EventBus → Kafka.

## Relacionado

- [[0007-k3d-orbstack-switch]]
- [[0027-orbstack-k3d-autodetect]]
- [[0003-vertx-for-distributed-poc]]

## Referencias

- OpenObserve: https://openobserve.ai/
- OpenTelemetry Java agent: https://opentelemetry.io/docs/instrumentation/java/automatic/
