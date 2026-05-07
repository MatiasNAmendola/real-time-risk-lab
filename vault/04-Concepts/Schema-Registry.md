---
title: Schema Registry
tags: [concept, pattern/async, kafka, schema]
created: 2026-05-07
---

# Schema Registry

Store centralizado de schemas Avro/JSON/Protobuf. Los producers registran schemas; los consumers validan contra ellos. Confluent Schema Registry es la implementación de referencia. Redpanda incluye un registry compatible.

## Cuándo usar

Uso multi-team de Kafka. Previene el modo de falla "el producer cambió el schema, el consumer explotó".

## Cuándo NO usar

Topics single-team, single-consumer donde el overhead de coordinación supera al riesgo.

## En este proyecto

Redpanda (usado en [[k8s-local]]) incluye un schema registry compatible con Confluent en el puerto 8081. Los eventos en [[java-vertx-distributed]] están documentados vía AsyncAPI 3.0 (formato de contrato complementario).

## Principio de diseño

"El registry es la única fuente de verdad sobre lo que va por la red. Sin él, estás haciendo evolución de schemas por convención y esperanza."

## Backlinks

[[Event-Versioning]] · [[DLQ]] · [[java-vertx-distributed]] · [[k8s-local]]
