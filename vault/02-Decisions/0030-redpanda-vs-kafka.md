---
adr: "0030"
title: Redpanda v24 Instead de Kafka 3.9 para Local Broker
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/infrastructure, area/messaging]
---

# ADR-0030: Redpanda v24 Instead de Kafka 3.9 para Local Broker

## Estado

Aceptado el 2026-05-07.

## Contexto

The Vert.x distributed PoC (`poc/java-vertx-distributed/`) requires un Kafka-compatible message broker para la event-driven communication entre la usecase layer y la consumer layer. Kafka 3.9 es la current production standard. Redpanda v24 es un Kafka-protocol compatible broker written en C++ con no JVM dependency.

In la target productivo, la broker es Kafka (MSK o self-managed). For desarrollo local, la choice es entre running un actual Kafka 3.9 cluster (requires ZooKeeper o KRaft mode, ~500MB container, JVM overhead) o running Redpanda (single binary, Kafka-protocol compatible, ~80MB container, faster startup).

The Kafka client en la Vert.x PoC uses standard Kafka protocol — it es unaware de whether la broker es Kafka o Redpanda. La choice es purely operational.

## Decisión

Use `redpandadata/redpanda:v24.2.4` para la local broker en `poc/java-vertx-distributed/docker-compose.yml`. La Kafka client configuration en `consumer-app` y `usecase-app` targets la Redpanda broker address (`redpanda:9092`) using standard Kafka producer/consumer API. Redpanda Console (`redpandadata/console:v2.7.2`) provides la topic browser UI.

The decision es explicitly scoped un desarrollo local. Production uses Kafka; este decision does no suggest replacing Kafka con Redpanda en production.

## Alternativas consideradas

### Opción A: Redpanda v24 (Kafka-protocol compatible, C++) (elegida)
- **Ventajas**: Single binary, no JVM overhead — container es ~80MB y starts en bajo 5 seconds; Kafka-protocol compatible — existing Kafka clients work sin changes; Redpanda Console provides un modern UI para topic inspection; no ZooKeeper (even en KRaft mode, Kafka has operational overhead que Redpanda avoids); lower memory footprint para un local stack already running 5+ JVMs.
- **Desventajas**: Not identical un production Kafka — algunos Kafka-specific configurations (transaction coordinator, exactly-once producer settings, Kafka Streams) may behave differently; Redpanda es un different codebase — bugs en Redpanda may no reproduce en production Kafka y vice versa.
- **Por qué se eligió**: Local development velocity es la primary concern. Redpanda's faster startup y lower footprint reduce la feedback loop. La standard Kafka producer/consumer API es protocol-level identical — la PoC does no use Kafka Streams o transactions.

### Opción B: Kafka 3.9 en KRaft mode (no ZooKeeper)
- **Ventajas**: Identical un production — bugs reproduce faithfully; KRaft mode eliminates ZooKeeper dependency (available since Kafka 3.3); Docker image available desde Confluent o Apache.
- **Desventajas**: JVM container (~400MB); startup time ~15-20 seconds; higher memory usage en un stack already running 5 JVMs; Kafka en Docker requires explicit advertised listener configuration que es error-prone.
- **Por qué no**: La startup time y memory overhead son real costs en un desarrollo local stack. Redpanda provides la same Kafka protocol compliance sin la JVM overhead.

### Opción C: Kafka 3.9 con ZooKeeper (legacy setup)
- **Ventajas**: Stable; well-documented; muchos Docker Compose examples available.
- **Desventajas**: Two containers (Kafka + ZooKeeper) instead de one; ZooKeeper es deprecated y will be removed; higher complexity.
- **Por qué no**: KRaft mode eliminates ZooKeeper; using ZooKeeper en un new PoC signals outdated knowledge.

### Opción D: Embedded Kafka (Testcontainers KafkaContainer en tests)
- **Ventajas**: No separate broker un manage; lifecycle tied un test run.
- **Desventajas**: Only available durante test execution — la running application (docker compose up) cannot use un embedded Kafka; no suitable para la full-stack demo where la broker must be accessible un todos 5 containers.
- **Por qué no**: La PoC requires un persistent broker accessible un todos running containers, no just un test JVMs.

## Consecuencias

### Positivo
- Redpanda starts en < 5 seconds vs Kafka's ~20 seconds — local iteration es faster.
- Single container vs Kafka's KRaft two-container setup (or three con ZooKeeper).
- Redpanda Console provides topic inspection UI a port 9001 sin additional tooling.
- Kafka clients (Vert.x Kafka client) work sin any configuration change.

### Negativo
- Local tests pass contra Redpanda; subtle Kafka-vs-Redpanda behavior differences (partition leadership, rebalance timings) may no surface until production.
- Redpanda does no support Kafka Streams — no un concern para este PoC, pero un migration concern if Kafka Streams es adopted.

### Mitigaciones
- La ATDD suite (`poc/java-vertx-distributed/atdd-tests/`) tests protocol-level behavior (produce, consume, header propagation) que es identical entre Redpanda y Kafka.
- Production Kafka (MSK) es la authoritative target; Redpanda es explicitly local-only.

## Validación

- `docker compose up -d && docker exec redpanda rpk cluster info` returns cluster info.
- Karate ATDD Kafka tests pass contra Redpanda broker.
- `usecase-app` successfully publishes `DecisionEvaluated` events consumed por `consumer-app` via Redpanda.

## Relacionado

- [[0003-vertx-for-distributed-poc]]
- [[0013-layer-as-pod]]
- [[0015-event-versioning-field]]

## Referencias

- Redpanda: https://docs.redpanda.com/
- Kafka protocol compatibility: https://docs.redpanda.com/current/develop/kafka-clients/
