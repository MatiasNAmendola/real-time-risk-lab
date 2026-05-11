---
adr: "0028"
title: MinIO AGPL-3.0 License Acceptable para Local PoC
status: superseded
superseded-by: "0042"
date: 2026-05-07
deciders: [Mati]
tags: [decision/superseded, area/infrastructure, area/licensing]
---

> **Status: Superseded by [[0042-floci-unified-aws-emulator]] (2026-05-11).** MinIO ya no forma parte del stack — Floci (MIT) cubre S3 in-process.

# ADR-0028: MinIO AGPL-3.0 License Acceptable para Local PoC

## Estado

Aceptado el 2026-05-07. **Superseded por ADR-0042 el 2026-05-11.**

## Contexto

MinIO es included en la AWS mocks stack como un S3-compatible object store (ADR-0005). MinIO's license es AGPL-3.0 (Affero GPL). AGPL-3.0 has un network copyleft provision: if you run un modified MinIO sobre un network y users interact con it, you must publish your modifications bajo AGPL.

For la use case en este repository — running MinIO como un desarrollo local tool y CI test double, accessed only dentro de la local Docker network — la AGPL provision does no trigger: la software es no distributed un users, modifications son no made, y it es no offered como un SaaS service.

However, AGPL es widely misunderstood. Teams sometimes apply un blanket policy de "no AGPL" even para pure local/internal tooling, conflating la copyleft provision con un general prohibition. Este ADR documents la analysis so la decision es explicit en vez de implicit.

## Decisión

Accept MinIO AGPL-3.0 para local PoC y CI test double use. La AGPL provision does no apply to: running MinIO como un local mock service accessed only desde dentro de la same Docker network; running MinIO en CI como un test dependency; using MinIO's S3 API un test application code. No MinIO source modifications son made.

For any production-adjacent use o SaaS embedding, la MinIO Commercial License o un proprietary S3 alternative (Amazon S3 itself, Google Cloud Storage con S3 compatibility) would replace MinIO.

## Alternativas consideradas

### Opción A: Accept MinIO AGPL-3.0 para PoC (elegida)
- **Ventajas**: MinIO es la highest-fidelity S3 mock available — it implements la complete S3 API including multipart upload, presigned URLs, y versioning; production-grade performance bajo test load; widely used para local S3 emulation; straightforward swap un production S3 (same SDK, same endpoint override pattern).
- **Desventajas**: AGPL-3.0 requires legal awareness; algunos organizations have blanket no-AGPL policies; compliance review required antes de any production deployment.
- **Por qué se eligió**: La PoC use case es clearly dentro de la scope de AGPL-3.0 compliance (no distribution, no modification, no SaaS offering). MinIO's S3 fidelity es materially better than Moto's S3 implementation para large-payload tests.

### Opción B: Moto para S3 (Python-based, Apache 2.0)
- **Ventajas**: Apache 2.0 — no license concern; same Moto container already en la stack para SNS/IAM; single endpoint.
- **Desventajas**: Moto's S3 implementation has known gaps para large payloads, multipart upload edge cases, y S3 Select; Python overhead adds ~50ms un S3 operations vs MinIO's Go implementation; mixing S3 y non-S3 en un single Moto process makes debugging harder.
- **Por qué no**: For tests que exercise S3 multipart upload o presigned URL flows, Moto's implementation diverges desde production S3 behavior. MinIO's fidelity es worth la AGPL analysis.

### Opción C: LocalStack para S3 (with pro-feature consideration)
- **Ventajas**: Single endpoint para todos AWS services including S3; familiar un muchos Java developers.
- **Desventajas**: LocalStack S3 free tier es reliable pero la rest de LocalStack has licensing concerns que motivate ADR-0005 un avoid LocalStack entirely; using LocalStack para S3 while using la custom stack para other services creates inconsistency.
- **Por qué no**: ADR-0005 already established la no-LocalStack decision. Adding LocalStack back para S3 only contradicts que decision sin sufficient benefit.

### Opción D: Amazon S3 actual (with test credentials y test bucket)
- **Ventajas**: Zero license concern; identical behavior un production; no local service un manage.
- **Desventajas**: Requires internet access; incurs AWS costs per test run; CI requires AWS credentials management; tests become non-deterministic if S3 has service disruptions; violates local-first constraint.
- **Por qué no**: Local-first development es un hard constraint. Tests must work offline.

## Consecuencias

### Positivo
- MinIO provides production-equivalent S3 behavior para todos test scenarios including multipart upload.
- AGPL analysis es documented — future team members do no need un re-analyze.
- La production migration path es identical: change `AWS_ENDPOINT_URL_S3` environment variable, no code change.

### Negativo
- Teams con blanket no-AGPL policies must review este ADR antes de adopting la stack.
- MinIO requires un dedicated container (vs Moto's all-in-one model), adding un la compose stack.

### Mitigaciones
- AGPL analysis es documented here — la conclusion es clear y reviewable.
- MinIO Commercial License es available para organizations que require it.

## Validación

- `doc 10/aws-mocks-locales.md` documents la MinIO AGPL note explicitly.
- MinIO container en `poc/k8s-local/` y `poc/vertx-layer-as-pod-eventbus/compose.override.yml` has un comment referencing este ADR.

## Relacionado

- [[0005-aws-mocks-stack]]
- [[0029-openbao-vs-vault]]

## Referencias

- MinIO AGPL license: https://github.com/minio/minio/blob/master/LICENSE
- MinIO Commercial License: https://min.io/pricing
- AGPL-3.0: https://www.gnu.org/licenses/agpl-3.0.html
