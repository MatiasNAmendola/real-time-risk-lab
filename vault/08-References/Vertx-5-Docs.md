---
title: Vert.x 5 Documentation
tags: [reference, vertx, java]
created: 2026-05-07
---

# Vert.x 5 Docs

Reference documentation for Vert.x 5 used in [[java-vertx-distributed]].

## Key URLs

- https://vertx.io/docs/vertx-core/java/ — Core Vert.x (event loop, verticles, event bus)
- https://vertx.io/docs/vertx-hazelcast/ — Hazelcast cluster manager
- https://vertx.io/docs/vertx-web/ — HTTP routing, SSE, WebSocket
- https://vertx.io/docs/vertx-kafka-client/ — Kafka producer/consumer

## Key Concepts

- **Verticle**: unit of deployment (like an actor). Each module deploys one or more verticles.
- **Event Bus**: in-process and clustered message passing. Used for inter-layer communication in [[Layer-as-Pod]].
- **Hazelcast TCP cluster manager**: replaces multicast (unreliable on Docker bridge) with explicit TCP member list.

## Backlinks

[[java-vertx-distributed]] · [[0003-vertx-for-distributed-poc]]
