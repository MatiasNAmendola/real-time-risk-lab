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

**Update 2026-05-11**: revertimos la decisión original ("mantener Redpanda")
después de validar Tansu end-to-end contra el monolito Vert.x sobre la pila
de compose (Apache Kafka JVM client + OTel instrumentation + producer real
escribiendo `risk-decisions partition=1 offset=0 errorCode=0`).

**Tansu pasa a ser el broker principal en TODO el repo** — compose, k8s-local,
tests de integración y ATDD. Redpanda queda eliminado:

- `compose/docker-compose.yml`: servicio `tansu` (`ghcr.io/tansu-io/tansu:0.6.0`)
  con `STORAGE_ENGINE="s3://tansu/"` apuntando a Floci. Topic seeding por
  `tansu-init` usando `confluentinc/cp-kafka:7.0.0` (Apache Kafka client).
- `poc/k8s-local/addons/50-tansu.yaml`: Deployment + Service + Job de seeding.
  Sin Helm: el chart de Redpanda se elimina.
- `tests/integration/`: `TansuContainer` con `STORAGE_ENGINE="memory://tansu/"`
  reemplaza al módulo Testcontainers de Redpanda. Misma 1-broker semantics.

**Caveats explícitos y documentados como deuda**:
- **librdkafka 2.x clients (kcat 1.7.1) NO funcionan** contra Tansu 0.6.0
  (`ApiVersionRequest` "Read underflow"). Mitigación: el smoke CLI (`cli/risk-smoke/`)
  usa `franz-go` (Go puro, no librdkafka) y los servicios Java usan el cliente
  Apache Kafka JVM — ambos verificados. Si en el futuro entra un client basado
  en librdkafka, hay que pinear a una versión vieja o esperar a Tansu 1.x.
- **EOS / transactions / rebalance bajo carga**: no testeados. Para PoC local
  basta; para producción habría que cerrar este gap (o seguir con Apache Kafka
  / Redpanda en prod y mantener Tansu sólo en dev).
- **Tansu sigue siendo pre-1.0** (`v0.6.0`, 2026-03-13). Asumimos breakage
  hasta el primer release estable.
- **🔴 k8s + S3 storage CreateTopics bug (descubierto 2026-05-11)**: el
  `tansu-init` Job en k8s (`poc/k8s-local/addons/50-tansu.yaml`) crashloopa
  con `org.apache.kafka.common.protocol.types.SchemaException: Buffer
  underflow while parsing response for request CREATE_TOPICS apiVersion=7`.
  Reproducible con `confluentinc/cp-kafka:7.0.0` y también con el cliente
  legacy `cp-kafka:5.5.0` (apiVersion=5). NO ocurre en compose ni en
  integration tests (`STORAGE_ENGINE=memory://`). El broker pod arranca y
  responde Metadata, pero el CreateTopics serializa una response
  malformada cuando el storage backend es S3 sobre Floci. Workaround
  inmediato: levantar topics manualmente vía PoC compose
  (`./poc/kafka-s3-tansu/scripts/up.sh`) ya que ahí el seeding sí
  funciona, o esperar fix upstream (issue a abrir en
  github.com/tansu-io/tansu). Si esto bloquea k8s en CI/demo, revertir
  el commit `23db11d` y volver a Redpanda en k8s.

**AutoMQ queda como candidato futuro** para producción real ("Kafka KRaft con
storage S3"), si y cuando hay apetito de mover esa parte fuera del scope local.

## Consecuencias

- Ventajas:
  - **Stack mínimo coherente**: un solo broker entre compose, k8s-local e
    integration tests. Misma imagen, mismos env vars, misma forma de seeding
    (Apache Kafka client). Cero diferencias dev↔prod-shape.
  - **Footprint colapsa**: ~7.7 MiB idle (Tansu) vs ~421 MiB (Redpanda) en
    compose. En k8s-local desaparecen los 1.5 Gi pre-reservados del chart de
    Redpanda y el Job de seeding requiere `<512Mi` ephemeral.
  - **Reutiliza Floci** (ADR-0042): Tansu apunta al bucket `tansu` ya seeded
    por `floci-init`. Cero infra de storage nueva.
  - **Narrativa**: el repo entero consume "menos AWS real" — S3 vía Floci,
    Kafka vía Tansu sobre Floci-S3. La práctica simula "Kafka cloud-native"
    sin precio.
- Desventajas (asumidas):
  - librdkafka 2.x clients quedan fuera del path soportado. Cualquier
    contributor que asuma "kcat funciona" se va a estrellar.
  - EOS bajo carga sigue siendo blind spot — riesgo si el load test (k6,
    ADR-0040) empuja patterns que requieren transactions.
  - Tansu 0.6.0 puede romper en upgrades menores; pineamos el digest.
- Mitigaciones:
  - Smoke CLI hardcoded a `franz-go` — comentario explícito en
    `cli/risk-smoke/internal/flows/kafka.go`.
  - `tansu-init` usa `confluentinc/cp-kafka` (Apache Kafka client) para
    seeding, no `rpk` ni `kafkacat`.
  - Imagen pineada a `0.6.0` (no `:latest`). Bumps son explícitos.
  - PoC `poc/kafka-s3-tansu/` queda como referencia histórica + scaffolding
    para validar futuras versiones.

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
- `poc/kafka-s3-tansu/README.md` — scaffold de referencia; el approach ya es
  mainstream tras la migración.
- [[0030-redpanda-vs-kafka]] — superseded en cuanto a "Redpanda como broker";
  conservar como contexto histórico.
- Issue tracker upstream: tansu-io/tansu sobre librdkafka 2.x compat.

## Open questions (re-evaluar cuando aplique)

- ¿Tansu 1.x resolverá `ApiVersionRequest` para librdkafka 2.x?
- ¿AutoMQ publicará guía de single-node dev con footprint medido < 1 GiB?
- ¿Existe interés productivo real en "Kafka-sobre-S3" o es performance art?
- ¿La PoC k6 expondrá problemas de throughput sostenido en Tansu que aquí
  no detectamos por estar idle?
