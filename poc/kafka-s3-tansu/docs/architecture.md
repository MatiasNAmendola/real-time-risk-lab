# Architecture — poc/kafka-s3-tansu

## Goal

Validate that a Java application speaking the Kafka wire protocol can publish
and consume from a Tansu broker whose log segments live in object storage
(Floci S3 emulator). No local broker disk, no Zookeeper, no KRaft quorum on
disk — segments are objects in S3.

## Topology

```
                 host
                   │
                   │  :9092 (published port, optional)
                   ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │ compose network: data-net + app-net                             │
   │                                                                 │
   │   ┌──────────────────────┐         ┌──────────────────────────┐ │
   │   │   Java app           │  Kafka  │   Tansu broker           │ │
   │   │   (e.g. monolith)    │ ──────► │   ghcr.io/tansu-io       │ │
   │   │                      │  :9092  │   /tansu:0.6.0           │ │
   │   │ KAFKA_BOOTSTRAP_     │         │                          │ │
   │   │ SERVERS=tansu:9092   │         │ STORAGE_ENGINE=          │ │
   │   └──────────────────────┘         │   s3://tansu/            │ │
   │                                    └──────────┬───────────────┘ │
   │                                               │ S3 API          │
   │                                               │ (AWS SDK)       │
   │                                               ▼                 │
   │                                    ┌──────────────────────────┐ │
   │                                    │   Floci (AWS emulator)   │ │
   │                                    │   floci/floci:latest     │ │
   │                                    │   :4566                  │ │
   │                                    │   bucket: tansu          │ │
   │                                    └──────────────────────────┘ │
   └─────────────────────────────────────────────────────────────────┘
```

## Why this layout

- **No broker disk state.** Tansu writes batch + watermark + metadata as
  ordinary S3 objects. The `compose-tansu-1` container has no volume; it can be
  killed and restarted, and the topic + offsets survive in S3.
- **Floci already adopted (ADR-0042).** This PoC adds zero new infra
  dependencies — only the `tansu` bucket is appended to the existing
  `floci-init` seeding job, and a single `tansu` service is added via override.
- **Drop-in for any PoC.** Swapping a PoC from Redpanda to Tansu is one env
  change: `KAFKA_BOOTSTRAP_SERVERS=redpanda:9092` → `tansu:9092`. Java code is
  untouched.

## Observed S3 object layout (live capture)

After producing one record to topic `smoke` partition 0:

```
clusters/tansu/meta.json                                                196 B
clusters/tansu/topics/smoke/partitions/0000000000/watermark.json         39 B
clusters/tansu/topics/smoke/partitions/0000000000/records/00000000000000000000.batch
                                                                         88 B
clusters/tansu/groups/consumers/console-consumer-31838.json             274 B
```

So segment layout, consumer group offsets, and cluster metadata are all
plain JSON / batch files in the bucket. This makes recovery and inspection
trivial during exploration.

## What we did NOT validate (be honest)

- Long-running soak with sustained throughput (no perf numbers in this PoC).
- Idempotent producer / EOS / transactions.
- Multi-partition consumer-group rebalance under churn.
- librdkafka modern clients (edenhill/kcat 1.7.1 fails ApiVersionRequest;
  see README "What doesn't work"). Java clients via `confluentinc/cp-kafka:7.0.0`
  are fine.

## References

- ADR-0042: `vault/02-Decisions/0042-floci-unified-aws-emulator.md`
- Upstream: https://github.com/tansu-io/tansu — version 0.6.0 (pre-1.0, Apache-2.0)
