---
adr: "0005"
title: AWS Mocks Stack (anti-LocalStack)
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/aws, area/testing, area/local-dev]
---

# ADR-0005: Curated AWS Mocks Stack Instead of LocalStack

## Estado

Aceptado el 2026-05-07.

## Contexto

Local AWS service emulation is required for Lambda RIE testing and SQS/SNS/S3/Secrets Manager integration without using real AWS accounts. LocalStack is the de facto standard choice for Java projects. As of 2026-05, LocalStack's community edition status is uncertain (research in doc 10 flagged potential discontinuation of the free tier), and Pro features (Lambda concurrency, Kinesis data streams, EKS simulation) are gate-locked.

The core requirement: S3-compatible object store, SQS-compatible queue, SNS, Secrets Manager / KMS, and DynamoDB — all accessible from Java SDK clients with `AWS_ENDPOINT_URL` overrides. A single-endpoint approach (LocalStack) is convenient; a per-service approach is more faithful to production (each service has its own endpoint, just like real AWS).

## Decisión

Use a curated per-service mock stack: Moto server (`motoserver/moto`) for SNS, IAM, and fallback; MinIO for S3 (AGPL-3.0 — see ADR-0028); ElasticMQ for SQS; OpenBao for Secrets Manager / KMS transit; DynamoDB Local for DynamoDB. Each service is the best-in-class mock for its AWS API.

## Alternativas consideradas

### Opción A: Per-service dedicated mocks (elegida)
- **Ventajas**: Each tool is the best-in-class implementation for its specific AWS API — MinIO's S3 fidelity vastly exceeds Moto's for large payloads; ElasticMQ's SQS fidelity exceeds both Moto and LocalStack for FIFO queue semantics; no single-vendor license risk; isolation — a MinIO crash does not affect SQS; easier debugging (one service per container).
- **Desventajas**: 5 containers instead of 1; startup order dependencies (health checks, `depends_on`); no single `AWS_ENDPOINT_URL` — must configure per-service endpoint URLs (`AWS_ENDPOINT_URL_S3`, `AWS_ENDPOINT_URL_SQS`, etc.); more Docker Compose complexity.
- **Por qué se eligió**: The per-service approach is more architecturally interesting to explain in a design review. "I use the right tool for each service" is a stronger answer than "I use LocalStack." The endpoint URL concern is mitigated by environment variable configuration.

### Opción B: LocalStack Community Edition
- **Ventajas**: Single URL for all AWS services (`http://localhost:4566`); well-documented; large community; single `AWS_ENDPOINT_URL` configuration.
- **Desventajas**: Uncertain community license status as of 2026-05; Pro features (Lambda concurrency > 1, Kinesis data streams, EKS) are gate-locked; reliability issues under load reported; known S3 multipart upload gaps; single process means a crash affects all services.
- **Por qué no**: License uncertainty + Pro gate-locking for features relevant to the target stack. The per-service stack provides better fidelity without the license risk.

### Opción C: Moto server standalone (all services via single Moto process)
- **Ventajas**: Apache 2.0 license; covers most AWS services; single endpoint; simpler than multi-container setup.
- **Desventajas**: Moto S3 fidelity gaps for large payloads and multipart; Moto SQS FIFO ordering is not production-faithful; Moto Secrets Manager is adequate for basic get/put but lacks KMS transit encryption emulation.
- **Por qué no**: For tests that exercise S3 multipart upload or SQS FIFO ordering, Moto's implementation diverges from production behavior. MinIO and ElasticMQ are materially better for these use cases.

### Opción D: AWS actual (with test credentials and isolated account/sandbox)
- **Ventajas**: Identical to production behavior; no emulation gaps; real SQS FIFO, real S3 versioning.
- **Desventajas**: Requires internet; incurs AWS costs per test run; CI requires AWS credentials management; local development requires VPN or internet; non-deterministic if AWS has service disruptions.
- **Por qué no**: Local-first development is a hard constraint. Tests must run offline.

## Consecuencias

### Positivo
- Each service behaves like its production equivalent for the APIs used in the PoC.
- License is clear: Moto (Apache 2.0), ElasticMQ (Apache 2.0), OpenBao (MPL-2.0), DynamoDB Local (AWS proprietary, free for dev), MinIO (AGPL-3.0 — acceptable for dev, see ADR-0028).
- The per-service stack is extensible: adding a new AWS service requires adding one container, not waiting for LocalStack to add it.

### Negativo
- 5 containers vs 1 — more memory, more startup time, more Docker Compose configuration.
- AWS SDK clients must be configured with per-service endpoint URL environment variables.
- Init job in k8s (70-aws-mocks.yaml) handles startup ordering — additional complexity in k3d.

### Mitigaciones
- Docker Compose with `depends_on` and `healthcheck` handles startup ordering.
- Environment variable template documented in doc 10.
- k8s init job creates required buckets/queues/secrets on first startup.

## Validación

- `docker compose up -d` in `poc/vertx-layer-as-pod-eventbus/` starts all 5 mock services.
- `aws --endpoint-url http://localhost:9000 s3 ls` returns empty list from MinIO.
- `aws --endpoint-url http://localhost:9324 sqs list-queues` returns queue list from ElasticMQ.
- Integration tests in `tests/integration/` pass against the mock stack.

## Relacionado

- [[0028-minio-agpl-acceptable]]
- [[0029-openbao-vs-vault]]
- [[0033-moto-inline-vs-localstack]]
- Docs: doc 10 (`docs/10-aws-mocks-locales.md`)

## Referencias

- Moto: https://github.com/getmoto/moto
- ElasticMQ: https://github.com/softwaremill/elasticmq
- MinIO: https://min.io/
- DynamoDB Local: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html
- doc 10: `docs/10-aws-mocks-locales.md`
