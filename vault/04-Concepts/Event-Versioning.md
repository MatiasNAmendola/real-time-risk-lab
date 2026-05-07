---
title: Event Versioning
tags: [concept, pattern/async, kafka, schema]
created: 2026-05-07
source: docs/06-eventos-versionados.md
---

# Event Versioning

Estrategia para evolucionar schemas de eventos sin romper consumers. Approaches clave: cambios additive-only (backward compatible), enforcement vía schema registry, naming versionado de topics, o envelope con campo `schemaVersion`.

## Cuándo usar

Todo topic Kafka compartido consumido por múltiples equipos o servicios. Especialmente importante durante la migración Lambda→EKS donde los consumers Lambda pueden quedar atrás.

## Cuándo NO usar

Event bus interno (single producer, single consumer bajo el mismo deployment).

## En este proyecto

Documentado en `docs/06-eventos-versionados.md`. Los eventos llevan campo `schemaVersion`. [[Schema-Registry]] impone compatibilidad. Ver [[java-vertx-distributed]] para el contrato AsyncAPI 3.0.

## Principio de diseño

"La evolución de schemas es un problema de coordinación de equipos disfrazado de problema técnico. El registry hace el contrato explícito y aplicado."

## Backlinks

[[Outbox-Pattern]] · [[Schema-Registry]] · [[DLQ]] · [[Communication-Patterns]]
