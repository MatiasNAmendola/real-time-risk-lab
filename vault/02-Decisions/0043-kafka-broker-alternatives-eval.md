---
adr: "0043"
title: Kafka-compatible broker — evaluación de alternativas
status: accepted
date: 2026-05-11
tags: [decision/accepted, kafka, broker, footprint]
---

# ADR-0043: Kafka-compatible broker — evaluación de alternativas

## Contexto

El stack local corría Redpanda con `mem_limit: 512Mi`, lo que falló en el chart
de k8s porque el preflight calcula `process(409Mi) + reserve(201Mi) ≈ 610Mi`.
Subir a `1Gi` aún crasheó seastar (`insufficient physical memory: 858MB needed,
815MB available`). Finalmente se settled en `1.5Gi` para que el broker arranque
en k8s-local. Esto motivó evaluar alternativas Kafka-wire-compatibles más
livianas o con storage cloud-native que reusen Floci-S3 (ADR-0042).

Se evaluaron 4 candidatos contra el baseline actual (Redpanda dev mode).

## Decisión

**Mantener Redpanda como broker principal**, ajustando el límite de memoria a
`1.5Gi` en `poc/k8s-local/addons/50-redpanda-values.yaml` y aplicando los args
de dev mode (`--mode dev-container --memory 512M --reserve-memory 0M
--overprovisioned`).

**Documentar Tansu como PoC paralela** en `poc/kafka-s3-tansu/` para
exploración del modelo Kafka-sobre-object-storage. **No reemplazar Redpanda
hasta que Tansu alcance 1.x y resuelva incompatibilidades con librdkafka 2.x.**

**AutoMQ queda registrado como candidato futuro** para una PoC posterior si
se quiere explorar el caso "Kafka productivo con storage S3" — no se invierte
ahora porque su footprint (~1.5-2 GiB) no mejora sobre Redpanda.

## Consecuencias

- Ventajas:
  - Cero cambio de runtime en el path principal. ATDD, smoke y E2E siguen
    contra Redpanda con garantías Kafka 100% (incluyendo EOS/transactions).
  - Tansu queda disponible como dev-tier ultralight (~7.7 MiB idle vs 421 MiB
    de Redpanda en el mismo host, medido con `docker stats`).
  - La narrativa "exploramos esto" queda capturada y reproducible.
- Desventajas:
  - El cluster k8s consume 1.5 GiB sólo para el broker. En máquinas con
    < 8 GiB de RAM esto deja poco margen para el resto del stack.
  - Tansu en PoC paralela duplica costos de mantenimiento si más adelante se
    decide migrar (env vars, healthchecks, formato de S3).
- Mitigaciones:
  - Documentar en `poc/kafka-s3-tansu/README.md` los gaps (`librdkafka 2.x`
    fail, EOS untested) para evitar adopción accidental.
  - Re-evaluar este ADR cuando Tansu publique `1.0` o cuando AutoMQ publique
    una guía oficial de single-node dev con footprint medido.

## Alternativas consideradas

### A) Apache Kafka KRaft `-Xmx256m`
- **Compatibilidad**: 100% wire protocol (es la referencia).
- **Footprint dev**: ~512 MiB single-broker con `KAFKA_HEAP_OPTS=-Xmx256m`.
- **Madurez**: máxima.
- **Storage**: disco local (PV en k8s).
- **Razón de descarte**: no mejora significativamente sobre Redpanda en
  footprint y agrega JVM al stack. Sin storage S3 cloud-native.

### B) Tansu (Rust, Apache-2.0)
- **Compatibilidad**: Kafka wire — verificada con `cp-kafkacat:7.0.0` y
  `cp-kafka:7.0.0` (Java AdminClient + producer + consumer). **Falla con
  `edenhill/kcat:1.7.1` (librdkafka 2.x)** en `ApiVersionRequest` "Read
  underflow".
- **Footprint dev**: 7.73 MiB idle (medido), boot 96 ms.
- **Madurez**: v0.6.0 (2026-03-13), pre-1.0, explícitamente experimental.
- **Storage**: pluggable — Postgres, SQLite, S3, memory. Probado contra
  Floci-S3 :4566 con `STORAGE_ENGINE="s3://tansu/"`. Records escritos
  visiblemente en `s3://tansu/clusters/tansu/topics/.../*.batch`.
- **Imagen**: `ghcr.io/tansu-io/tansu:0.6.0` (digest pineado).
- **Razón de descarte como principal**: incompat librdkafka 2.x +
  EOS/transactions/rebalance untested + pre-1.0 status.
- **Adoptado como PoC**: `poc/kafka-s3-tansu/` (commit 39be14b).

### C) AutoMQ (Apache-2.0)
- **Compatibilidad**: 100% wire protocol (es Kafka KRaft con storage
  replazado).
- **Footprint dev**: docker-compose oficial pide ≥4 GiB RAM; single-broker
  estimado ~1.5-2 GiB. No mejora sobre Redpanda.
- **Madurez**: prod en Grab, Tencent, JD.com, Honda. v1.6.6 (2026-04-22).
- **Storage**: S3 obligatorio. Compatible con MinIO / Floci.
- **EOS / transactions**: heredadas de Kafka KRaft, deberían funcionar.
- **Razón de descarte ahora**: no resuelve el problema (footprint). Vale la
  pena como PoC arquitectónica futura ("brokers stateless + S3 = nueva
  topología") pero no como reemplazo de Redpanda en este momento.

### D) Floci MSK
- **Compatibilidad**: no implementa wire protocol. Dos modos:
  - `msk.mock = true`: responde sólo control-plane AWS (CreateCluster,
    DescribeCluster) con bootstrap dummy `localhost:9092`. **No spawnea
    nada** — útil sólo para validar flows de AWS API.
  - `msk.mock = false` (default): spawnea container Redpanda vía
    Docker-in-Docker (`RedpandaManager.startContainer()`). Mismo footprint
    que Redpanda standalone + overhead Floci.
- **Razón de descarte**: no aporta nada nuevo. Mock no tiene broker; modo
  real es Redpanda con overhead extra.

### E) NATS JetStream + Kafka bridge
- **Compatibilidad**: vía bridge (no wire-nativa).
- **Footprint**: ~64 MiB JetStream + bridge.
- **Razón de descarte**: indirección extra, perdés garantías Kafka nativas,
  no calza con la narrativa "Floci-S3 como WAL".

## Datos medidos (verbatim de `docker stats --no-stream`)

```
NAME                 MEM USAGE / LIMIT     CPU %
compose-redpanda-1   421.2MiB / 768MiB     0.99%
compose-tansu-1      7.73MiB / 256MiB      0.00%
compose-floci-1      48.77MiB / 256MiB     0.10%
```

Tansu + Floci compartido: **~56.5 MiB** vs Redpanda standalone **~421 MiB**.
Marginal en stack con Floci ya levantado: **~7.7 MiB**.

## Relacionado

- [[0042-floci-unified-aws-emulator]] — provee S3 para Tansu/AutoMQ.
- [[0040-k6-for-load-testing]] — el load test eventualmente cerrará el gap de
  EOS/throughput sostenido.
- `poc/kafka-s3-tansu/README.md` — PoC vigente.
- Issue tracker upstream: tansu-io/tansu sobre librdkafka 2.x compat.

## Open questions (re-evaluar cuando aplique)

- ¿Tansu 1.x resolverá `ApiVersionRequest` para librdkafka 2.x?
- ¿AutoMQ publicará guía de single-node dev con footprint medido < 1 GiB?
- ¿Existe interés productivo real en "Kafka-sobre-S3" o es performance art?
- ¿La PoC k6 expondrá problemas de throughput sostenido en Tansu que aquí
  no detectamos por estar idle?
