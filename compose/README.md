# Shared Docker Compose stack

Base infra (Postgres, Valkey, Tansu, Floci, OpenObserve, OTel collector) lives here. Each PoC adds its own apps via override files.

## Usage

```bash
# 1. Solo infra (sin apps)
docker compose -f compose/docker-compose.yml up -d

# 2. Infra + dev tools (currently a no-op after the Tansu migration; no broker
#    UI is bundled with Tansu — use kafka-topics CLI for inspection)
docker compose -f compose/docker-compose.yml -f compose/docker-compose.dev-tools.yml up -d

# 3. Infra + vertx-layer-as-pod-eventbus (most common)
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/vertx-layer-as-pod-eventbus/compose.override.yml \
  up -d

# 4. Infra + vertx-monolith-inprocess
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/vertx-monolith-inprocess/compose.override.yml \
  up -d

# 5. Infra + selected PoCs side-by-side
REPO_ROOT="$(pwd)" docker compose \
  -f compose/docker-compose.yml \
  -f poc/vertx-layer-as-pod-eventbus/compose.override.yml \
  -f poc/vertx-monolith-inprocess/compose.override.yml \
  up -d
```

Use `./nx up vertx-layer-as-pod-eventbus` for option 3 above.

## Why base + overrides?

Composability. Infra services have no opinion on which app consumes them. Adding a new PoC means creating one override file — the infra stack is untouched.

## Networks

Base infra defines three shared networks:

- `app-net`: Java app services and Hazelcast TCP cluster traffic. Apps that need clustered EventBus membership join this network.
- `data-net`: database/cache/secrets and base async infrastructure used by init jobs (Postgres, Valkey, Tansu, Floci).
- `telemetry-net`: OTel collector, OpenObserve, and apps emitting telemetry.

The `poc/vertx-layer-as-pod-eventbus/compose.override.yml` override adds a fourth PoC-specific network:

- `async-net`: Tansu/Floci plus `usecase-app` and `consumer-app`. This lets usecase/consumer publish and consume async outputs without joining `data-net`; `repository-app` remains the only app on `data-net` for DB/secrets access.

There is no `ingress-net` or `eventbus-net` anymore. Ingress is controlled by service `ports`, and clustered EventBus traffic uses `app-net`.

## REPO_ROOT

Override files use `${REPO_ROOT:-.}` to resolve volume paths (rules-config) relative to the repo root. Always set it before running compose with an override:

```bash
export REPO_ROOT="$(pwd)"
```

Or inline as shown in the usage examples above. When running from the repo root the default `.` fallback also works for relative paths within the override context, but explicit REPO_ROOT is safer.

## Test runner and infra

Suites tagged `needs_infra: compose` in `.ai/test-groups.yaml` use the vertx-layer-as-pod-eventbus profile by default. To override, set `RISK_PROFILE=vertx-monolith-inprocess` before running the test runner — the runner will pick up the env and select the appropriate override. This is intentionally minimal; the runner doc is the authoritative source.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Base infra (Postgres, Valkey, Tansu, Floci, OpenObserve, OTel) |
| `docker-compose.dev-tools.yml` | No-op override after the Tansu migration (no bundled broker UI) |
| `otel-collector-config.yaml` | OTel collector pipeline config (moved from poc/vertx-layer-as-pod-eventbus/) |
