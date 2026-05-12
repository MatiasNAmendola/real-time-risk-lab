---
title: Schema Registry
tags: [concept, pattern/async, kafka, schema]
created: 2026-05-07
---

# Schema Registry

Store centralizado de schemas Avro/JSON/Protobuf. Los producers registran schemas; los consumers validan contra ellos. Confluent Schema Registry es la implementación de referencia. Tansu trae capacidades de schemas en el broker, pero en este repo no lo operamos como reemplazo del Confluent Schema Registry REST API.

## Cuándo usar

Uso multi-team de Kafka. Previene el modo de falla "el producer cambió el schema, el consumer explotó".

## Cuándo NO usar

Topics single-team, single-consumer donde el overhead de coordinación supera al riesgo.

## En este proyecto

Tansu (usado en [[k8s-local]]) queda limitado al broker Kafka-wire local. Los eventos en [[vertx-layer-as-pod-eventbus]] están documentados vía AsyncAPI 3.0; si se requiere registry REST compatible con Confluent, se agrega explícitamente como componente aparte.

## Principio de diseño

"El registry es la única fuente de verdad sobre lo que va por la red. Sin él, estás haciendo evolución de schemas por convención y esperanza."

## Backlinks

[[Event-Versioning]] · [[DLQ]] · [[vertx-layer-as-pod-eventbus]] · [[k8s-local]]
