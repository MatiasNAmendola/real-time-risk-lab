# Shared Docker Compose stack

Base infra (Postgres, Valkey, Redpanda, MinIO, ElasticMQ, Moto, OpenBao, OpenObserve, OTel collector) lives here. Each PoC adds its own apps via override files.

## Usage

```bash
# 1. Solo infra (sin apps)
docker compose -f compose/docker-compose.yml up -d

# 2. Infra + dev tools (Redpanda Console)
docker compose -f compose/docker-compose.yml -f compose/docker-compose.dev-tools.yml up -d

# 3. Infra + Vertx distributed (most common)
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/java-vertx-distributed/compose.override.yml \
  up -d

# 4. Infra + java-monolith
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/java-monolith/compose.override.yml \
  up -d

# 5. Infra + both PoCs side-by-side
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/java-vertx-distributed/compose.override.yml \
  -f poc/java-monolith/compose.override.yml \
  up -d
```

Use `./nx up vertx` as shorthand for option 3 above.

## Why base + overrides?

Composability. Infra services have no opinion on which app consumes them. Adding a new PoC means creating one override file — the infra stack is untouched.

## Networks

- `app-net`: Java apps and Hazelcast cluster. All apps that need cluster membership must join this network.
- `data-net`: data services and apps that consume them (Postgres, Valkey, Redpanda, MinIO, ElasticMQ, Moto, OpenBao).
- `telemetry-net`: OTel collector, OpenObserve, and all apps emitting telemetry.

3 networks collapsed from 5 — Hazelcast TCP discovery requires apps to share a single network. Credential-level isolation replaces network-level isolation between layers (only repository-app receives DB credentials).

## REPO_ROOT

Override files use `${REPO_ROOT:-.}` to resolve volume paths (rules-config) relative to the repo root. Always set it before running compose with an override:

```bash
export REPO_ROOT="$(pwd)"
```

Or inline as shown in the usage examples above. When running from the repo root the default `.` fallback also works for relative paths within the override context, but explicit REPO_ROOT is safer.

## Test runner and infra

Suites tagged `needs_infra: compose` in `.ai/test-groups.yaml` use the vertx profile by default. To override, set `RISK_PROFILE=monolith` before running the test runner — the runner will pick up the env and select the appropriate override. This is intentionally minimal; the runner doc is the authoritative source.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Base infra (Postgres, Valkey, Redpanda, MinIO, ElasticMQ, Moto, OpenBao, OpenObserve, OTel) |
| `docker-compose.dev-tools.yml` | Optional Redpanda Console UI |
| `otel-collector-config.yaml` | OTel collector pipeline config (moved from poc/java-vertx-distributed/) |
| `redpanda-console-config.yaml` | Redpanda Console broker config (moved from poc/java-vertx-distributed/) |
