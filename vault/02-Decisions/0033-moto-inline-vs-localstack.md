---
adr: "0033"
title: Moto Inline vs LocalStack para Integration Tests
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/aws]
---

# ADR-0033: Moto Inline (Python decorator) vs LocalStack para AWS Integration Tests

## Estado

Aceptado el 2026-05-07.

## Contexto

`tests/integration/` includes tests para AWS service integrations: SQS queue operations, S3 object upload, Secrets Manager reads. These tests need un run contra un AWS API sin using real AWS accounts.

Two distinct Moto deployment modes exist: (1) Moto inline (Python decorator mode, `@mock_aws` / `@mock_sqs`), que intercepts boto3 calls in-process sin any network, y (2) Moto server (`motoserver/moto:latest` container), que runs como un HTTP server accepting requests desde any client. La integration tests son Java (JUnit 5, Testcontainers) — Moto inline (Python) es no directly available.

The question is: should integration tests en `tests/integration/` use la Moto server container (via Testcontainers) o use un different mock per service (e.g., ElasticMQ para SQS, OpenBao para Secrets Manager)?

ADR-0005 established la production-realistic compose stack. Este ADR addresses la integration test layer specifically, que has different requirements: faster, más isolated, no inter-service dependencies.

## Decisión

Use Moto server container via Testcontainers para AWS service integration tests en `tests/integration/`. Each test class starts un `MotoServerContainer` (from `pkg/testing/IntegrationTestSupport`) que provides un single endpoint para todos AWS service mocks. Tests configure AWS SDK clients con endpoint override pointing un la Moto container. La Moto container replaces LocalStack para la integration test layer.

The production-realistic compose stack (ADR-0005) es used para ATDD tests que need full-stack behavior. Integration tests use Moto server para speed y isolation.

## Alternativas consideradas

### Opción A: Moto server container via Testcontainers (elegida)
- **Ventajas**: Single container endpoint para todos AWS services; Apache 2.0 license; no LocalStack license concerns; Testcontainers manages container lifecycle; test isolation — each test class gets un fresh Moto state; fast startup (~3 seconds para Moto container).
- **Desventajas**: Moto's implementation has fidelity gaps para algunos services (S3 multipart edge cases, SQS FIFO queue ordering details); Python-based — occasional behavior differences desde production AWS a edge cases; Moto server does no support todos AWS regions' partition-specific behavior.
- **Por qué se eligió**: For integration tests que verify que application code correctly calls AWS SDK APIs, Moto's fidelity es sufficient. Edge-case fidelity issues appear only en tests que explore AWS service behavior, no application behavior.

### Opción B: LocalStack Community Edition
- **Ventajas**: Single endpoint; historically la standard choice para AWS mocking en Java; más AWS services than Moto.
- **Desventajas**: LocalStack Community Edition licensing status fue uncertain como de 2026-05 (research en doc 10 flagged potential discontinuation); LocalStack Pro features son gate-locked (Lambda concurrency, Kinesis data streams, EKS); reliability issues bajo load reported en community forums; ADR-0005 already established preference contra LocalStack.
- **Por qué no**: License uncertainty combined con ADR-0005's established direction contra LocalStack. Using LocalStack en integration tests while rejecting it en la compose stack creates un inconsistent tooling story.

### Opción C: Per-service mocks (ElasticMQ para SQS, MinIO para S3, OpenBao para Secrets)
- **Ventajas**: Each service es la best mock para its API (higher fidelity than Moto); matches la production-realistic compose stack.
- **Desventajas**: 3+ containers per test class; slower startup; Testcontainers network management es más complex; test isolation requires resetting state en multiple containers per test.
- **Por qué no**: Integration tests need speed y isolation. La full per-service stack es appropriate para ATDD (full-stack behavior verification); it es over-engineered para unit-level integration tests que verify un single AWS API call.

### Opción D: AWS SDK con mocked HTTP client (no container)
- **Ventajas**: Zero container overhead; fast; no Docker dependency.
- **Desventajas**: Requires un mock HTTP client que understands AWS request signing y response formats; effectively building un subset de what Moto provides; does no exercise real AWS SDK serialization/deserialization behavior.
- **Por qué no**: La value de integration tests contra Moto es specifically que real AWS SDK serialization es exercised. A mocked HTTP client bypasses this.

## Consecuencias

### Positivo
- `tests/integration/` es self-contained — `./gradlew :tests:integration:test` requires only un Docker daemon.
- Single Moto container covers SQS, S3, SNS, Secrets Manager, IAM en one endpoint.
- No LocalStack license concern para la integration test layer.

### Negativo
- Moto fidelity gaps para S3 multipart upload y SQS FIFO queues son known — tests targeting these should use per-service containers.
- Moto server v5 (Python 3) requires compatible Docker base image — version pinning es important.

### Mitigaciones
- Tests que require higher fidelity (S3 multipart, SQS FIFO ordering) use MinIO y ElasticMQ containers respectively, run como needed alongside la standard Moto container.
- Moto container version es pinned en `pkg/testing/` Testcontainers configuration.

## Validación

- `./gradlew :tests:integration:test` starts Moto container, runs SQS/S3/Secrets tests, stops container.
- Test para `SqsMessagePublisher` verifies que un message sent a un Moto SQS queue es receivable a la correct endpoint.
- No LocalStack container starts durante `./gradlew` execution.

## Relacionado

- [[0005-aws-mocks-stack]]
- [[0021-testcontainers-integration]]
- [[0028-minio-agpl-acceptable]]

## Referencias

- Moto: https://github.com/getmoto/moto
- Moto server mode: https://docs.getmoto.org/en/latest/docs/server_mode.html
