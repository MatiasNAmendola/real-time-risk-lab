# poc/kafka-s3-tansu

Exploration PoC: run a **Tansu** broker (Rust, Apache-2.0, Kafka-wire-compatible)
on top of **Floci S3** (our shared AWS emulator, see [ADR-0042](../../vault/02-Decisions/0042-floci-unified-aws-emulator.md))
and check whether it can replace Redpanda for local dev / lightweight CI.

> **Status update (2026-05-11)** — the answer was *yes*. After validating
> this PoC end-to-end against the Vert.x monolith, **Tansu is now the
> mainstream broker for the entire repo**: compose stack, k8s-local PoC,
> and the Testcontainers integration suite all run against
> `ghcr.io/tansu-io/tansu:0.6.0`. Redpanda is removed.
>
> See the updated **[ADR-0043](../../vault/02-Decisions/0043-kafka-broker-alternatives-eval.md)**
> for the migration decision, the verified compat caveats (librdkafka 2.x
> clients fail; the smoke CLI uses `franz-go` to sidestep this), and the
> documented gaps (EOS/transactions untested).
>
> This PoC directory survives as the reference scaffold (compose override,
> footprint comparison, architecture notes) and as a stable smoke target for
> validating future Tansu releases.

## Status

**Mainstream.** Tansu `0.6.0` is **pre-1.0** and explicitly experimental upstream,
yet validated end-to-end here against the Vert.x monolith. This PoC originally
demonstrated that the binary boots, accepts Kafka wire requests from Java
clients, and persists records as plain S3 objects in Floci. **It is not yet a
production recommendation** — EOS/transactions and rebalance under load are
still untested.

- Image: `ghcr.io/tansu-io/tansu:0.6.0` (pulled, digest pinned in compose).
- License: Apache-2.0.
- Upstream: <https://github.com/tansu-io/tansu>.

## What works (verified live)

All of the following ran successfully against `ghcr.io/tansu-io/tansu:0.6.0`
on macOS arm64 / OrbStack on 2026-05-11:

- `docker pull ghcr.io/tansu-io/tansu:0.6.0` → succeeded.
- Tansu boot: `ready in 96ms` log line; broker registers itself under
  `STORAGE_ENGINE="s3://tansu/"`.
- Floci bucket seeding via the shared `floci-init` job (the `tansu` bucket is
  appended to its bucket list, idempotent).
- Metadata handshake from `confluentinc/cp-kafkacat:7.0.0`
  (`kafkacat -L -b tansu:9092`) lists `1 broker, 0 topics` cleanly.
- Topic creation via `confluentinc/cp-kafka:7.0.0` `kafka-topics --create`.
- Producer round-trip: a message written by `cp-kafkacat -P` lands as an
  S3 object at
  `s3://tansu/clusters/tansu/topics/smoke/partitions/0000000000/records/00000000000000000000.batch`
  (verified with `aws s3 ls`).
- Consumer round-trip: `kafka-console-consumer --from-beginning` reads the
  same message back.
- Consumer-group state lands at `s3://tansu/clusters/tansu/groups/consumers/<id>.json`.

## What doesn't work / not validated

- **`edenhill/kcat:1.7.1` (modern librdkafka)** fails the `ApiVersionRequest`
  against Tansu 0.6.0:

  ```
  FAIL ApiVersionRequest failed: Local: Read underflow:
       probably due to broker version < 0.10
  ```

  Older `confluentinc/cp-kafkacat:7.0.0` (librdkafka ~1.7) negotiates fine.
  Java AdminClient + producer + consumer (cp-kafka 7.0.0) all work. This is a
  **real Kafka-wire incompatibility on the API-versions path**, not a config
  issue. Newer official Kafka / Confluent clients (post-3.x) have not been
  tested against this image.
- **Idempotent producer** (`enable.idempotence=true`) — **not tested**.
- **Transactions / EOS** — **not tested**.
- **Consumer-group rebalance under churn / multi-consumer** — **not tested**;
  only a single console consumer was exercised.
- **Sustained throughput** (e.g. our 150 TPS target) — **not benchmarked**.
- **Schema Registry / Pandaproxy / Kafka Connect interop** — N/A, Tansu does
  not expose any of those.
- **Reading the existing repo's `risk-decisions` topic** with a real
  monolith — **not wired**. The override file does not (yet) point the
  monolith at Tansu; that's a follow-up.

## Open questions

1. Does `enable.idempotence=true` + producer epochs work end-to-end on 0.6.0,
   or is the producer-id allocation path stubbed? (Need a small Java probe.)
2. Consumer-group rebalance latency on object-storage — how bad is it when
   the offset commit path is S3 PUT?
3. Behavior under Floci restart (in-memory) vs real S3 / MinIO with durable
   disk — does Tansu recover cluster metadata from S3, or expect a
   bootstrap-on-empty path?
4. Wire compatibility with current librdkafka (≥ 2.x) — this PoC found a
   regression with kcat 1.7.1; needs an upstream issue or version pin doc.
5. Memory under load (idle is 7.73 MiB; what does the working set look like
   at 150 TPS sustained?).

## How to run

From the repo root:

```bash
# Up: brings up floci + floci-init + tansu (uses shared compose/docker-compose.yml).
./poc/kafka-s3-tansu/scripts/up.sh

# Smoke test (produce + consume):
docker run --rm --network=compose_data-net confluentinc/cp-kafka:7.0.0 \
  kafka-topics --bootstrap-server tansu:9092 --create --topic smoke \
  --partitions 1 --replication-factor 1

echo "hello-tansu" | docker run --rm -i --network=compose_data-net \
  confluentinc/cp-kafkacat:7.0.0 kafkacat -P -b tansu:9092 -t smoke -p 0

docker run --rm --network=compose_data-net confluentinc/cp-kafka:7.0.0 \
  kafka-console-consumer --bootstrap-server tansu:9092 --topic smoke \
  --from-beginning --timeout-ms 5000

# Inspect what landed in S3:
docker run --rm --network=compose_data-net \
  -e AWS_ACCESS_KEY_ID=test -e AWS_SECRET_ACCESS_KEY=test \
  -e AWS_DEFAULT_REGION=us-east-1 amazon/aws-cli:latest \
  --endpoint-url=http://floci:4566 s3 ls s3://tansu/ --recursive

# Down (just tansu — leaves shared floci infra running for other PoCs):
./poc/kafka-s3-tansu/scripts/down.sh
```

If you want a one-shot full teardown:

```bash
docker compose -f compose/docker-compose.yml \
               -f poc/kafka-s3-tansu/compose.override.yml down --remove-orphans
```

## Tradeoffs vs Redpanda

| Dimension                      | Redpanda 24.2.4              | Tansu 0.6.0 on Floci S3            |
|--------------------------------|------------------------------|------------------------------------|
| Idle RAM                       | ~421 MiB                     | ~7.7 MiB (broker) + Floci shared   |
| Local disk needed              | Yes (`/var/lib/redpanda`)    | None (objects in S3)               |
| Startup time                   | ~3-8 s with healthcheck      | "ready in 96 ms"                   |
| Maturity                       | Stable, prod-deployed        | Pre-1.0, exploratory               |
| Schema Registry / Pandaproxy   | Yes (built-in)               | No                                 |
| EOS / transactions             | Yes                          | **Not validated** here             |
| Wire compat (librdkafka 2.x)   | Full                         | **Partial — see kcat 1.7.1 issue** |
| License                        | BSL → Apache-2.0 (per-feature) | Apache-2.0                       |

See [docs/footprint-comparison.md](docs/footprint-comparison.md) for the raw
`docker stats` capture.

## Recommendation

- **Use this PoC for:** learning Kafka-on-S3 architecture, ultra-light local
  dev stacks where every megabyte of laptop RAM matters, internal demos of
  "Kafka with no broker disk".
- **Do NOT use this PoC for:** production traffic, EOS-critical pipelines,
  anything that depends on Schema Registry / Pandaproxy / Connect, anything
  that targets modern librdkafka clients without first testing the wire path.
- As a Redpanda replacement in this repo today: **not yet**. Revisit when
  Tansu reaches 1.x and the librdkafka 2.x compat path is fixed. Until then,
  Redpanda stays as the default broker in `compose/docker-compose.yml`.

## Citations

- [ADR-0042 — Floci unified AWS emulator](../../vault/02-Decisions/0042-floci-unified-aws-emulator.md)
- [Tansu upstream README & example.env](https://github.com/tansu-io/tansu)
  (env vars verbatim from `main` branch `example.env`, version 0.6.0)
- [docs/architecture.md](docs/architecture.md) — topology diagram + observed
  S3 object layout
- [docs/footprint-comparison.md](docs/footprint-comparison.md) — measured
  RAM numbers
