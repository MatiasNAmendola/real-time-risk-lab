---
adr: "0042"
title: Floci as Unified AWS Emulator (replaces MinIO + ElasticMQ + Moto + OpenBao)
status: accepted
date: 2026-05-11
deciders: [Mati]
tags: [decision/accepted, area/aws, area/testing, area/local-dev, area/infra]
supersedes:
  - "0005"
  - "0028"
  - "0029"
  - "0033"
---

# ADR-0042: Floci as Unified AWS Emulator

## Estado

Aceptado el 2026-05-11. Supersede a ADR-0005, ADR-0028, ADR-0029, ADR-0033.

## Contexto

ADR-0005 eligió una pila per-servicio de mocks AWS: MinIO (S3, AGPL-3.0), ElasticMQ (SQS), Moto (SNS/Secrets/KMS/STS/IAM) y OpenBao (Secrets/KMS estilo Vault). Esa decisión se tomó en mayo 2026 con dos preocupaciones explícitas: (1) el estatus incierto de LocalStack Community y (2) la fidelidad limitada de Moto para S3 y SQS FIFO.

Desde entonces:

1. **LocalStack Community fue sunset en marzo 2026** (auth-token obligatorio, sin parches de seguridad). La preocupación de ADR-0005 se materializó.
2. **Floci** (https://github.com/floci-io/floci) — emulador AWS unificado, licencia MIT, construido sobre Quarkus + GraalVM native — apareció como reemplazo drop-in de LocalStack con cobertura más amplia (27+ servicios, incluyendo Cognito, MSK, RDS, Athena, EKS, ECS, etc.) y huella muy reducida (~40 MB de imagen, ~24 ms de arranque, ~13 MiB idle).
3. La pila actual del lab tiene **4 contenedores, 4 endpoints, 4 binarios cliente** (`mc`, `aws --endpoint-url`, `bao`, `boto3`) y **4 sets de env vars** (`AWS_ENDPOINT_URL_S3`, `AWS_ENDPOINT_URL_SQS`, `AWS_ENDPOINT_URL_SECRETSMANAGER`, `VAULT_ADDR`). El costo operativo y de explicación supera ya la ventaja de fidelidad por servicio.
4. **OpenBao** se introdujo (ADR-0029) por separado de los mocks AWS porque ni MinIO ni ElasticMQ resuelven secrets/KMS, y Moto tenía gaps en KMS transit. Con Floci, Secrets Manager + KMS están en el mismo proceso que SQS/S3/SNS — no hay razón para mantener un componente PKI/transit aparte. **OpenBao se elimina completamente.**

## Decisión

Adoptar **Floci** como único emulador AWS local. Reemplaza:

| Componente legacy | Reemplazo en Floci |
|---|---|
| MinIO :9000 (S3) | Floci :4566 (S3 in-process) |
| ElasticMQ :9324 (SQS) | Floci :4566 (SQS in-process) |
| Moto :5000 (SNS / Secrets / KMS / STS / IAM) | Floci :4566 (todos in-process) |
| OpenBao :8200 (Secrets/KMS estilo Vault) | Floci :4566 (Secrets Manager + KMS AWS-API) |

**Imagen elegida**: `floci/floci:latest` (GraalVM native binary, ~40 MB). El tag `latest-compat` (con AWS CLI + boto3 bundled) **no** se usa como runtime; los jobs de seeding usan `amazon/aws-cli` separadamente para preservar la separación de imagen runtime vs imagen tooling.

**Endpoint único**: `FLOCI_ENDPOINT=http://floci:4566` (en compose) / `http://floci.aws-mocks.svc.cluster.local:4566` (en k8s) / `http://localhost:4566` (en dev host). Todos los AWS SDK clients usan ese endpoint como `endpointOverride`.

**Health endpoint**: `GET /_floci/health` (puerto 4566). Verificado contra el source en `/tmp/floci-audit/floci/docker/Dockerfile.native`.

## Alternativas consideradas

### Opción A: Floci (elegida)
- **Ventajas**: MIT, 1 contenedor, 1 endpoint, 1 set de env vars, cobertura 46 servicios, drop-in replacement de LocalStack (puerto 4566, env vars LocalStack auto-traducidas), binario nativo (~24 ms cold start, ~13 MiB RAM), tests de compatibilidad propios (1850+ tests contra 6 SDKs). Permite eliminar OpenBao porque Secrets Manager + KMS están en el mismo proceso AWS-API.
- **Desventajas**: Pierde fidelidad "real-S3" de MinIO para tests muy específicos de multipart/large-object (no relevantes en este lab, payloads <100 KB). Producto joven (cf. LocalStack 9 años, MinIO 10 años) — posible volatilidad en APIs.
- **Por qué se eligió**: La fidelidad extra de MinIO/ElasticMQ no se ejercita en los tests actuales. La simplicidad operativa (1 contenedor) más la cobertura ampliada (KMS verify/sign, Cognito, etc.) compensan con holgura el riesgo de madurez.

### Opción B: LocalStack Community Edition con auth-token
- **Ventajas**: Madurez, comunidad grande.
- **Desventajas**: Requiere registro y token; security updates congelados desde marzo 2026; Pro features siguen gate-locked. Compromete el principio "local sin credenciales externas".
- **Por qué no**: El gate de auth-token rompe el flujo offline / CI sin credenciales que este lab necesita.

### Opción C: Mantener la pila per-servicio (status quo de ADR-0005)
- **Ventajas**: Funciona. Cada servicio es best-in-class para su API.
- **Desventajas**: 4 contenedores, 4 endpoints, OpenBao no es AWS-shaped (requiere VAULT_ADDR + token, no SDK AWS), Moto requiere init job con boto3, ElasticMQ requiere init job con aws-cli, MinIO requiere init job con mc — 3 estilos distintos de seeding.
- **Por qué no**: Cost-of-explain alto en code review. La razón original ("LocalStack es incierto, Moto tiene gaps") se resuelve mejor con Floci en 2026.

### Opción D: LocalStack Pro con cuenta de empresa
- Fuera del scope de un lab personal. Cost.

## Consecuencias

### Positivo
- 1 contenedor en lugar de 4 (compose y k8s).
- 1 env var (`FLOCI_ENDPOINT`) en lugar de 4 (`AWS_ENDPOINT_URL_*` + `VAULT_ADDR`).
- 1 imagen de seeding (`amazon/aws-cli`) que cubre S3 + SQS + SNS + Secrets + KMS — antes había 3 imágenes distintas (`minio/mc`, `python:3.12-slim` con boto3, `openbao/openbao`, `amazon/aws-cli`).
- Eliminación de OpenBao reduce ~256 MB de RAM y un binario CLI propietario (`bao`) en el grupo de setup.
- Licencia simplificada: MIT (Floci) en lugar de MIT + Apache 2.0 + AGPL-3.0 + MPL-2.0 mezclados. Cierra la deuda de ADR-0028 (AGPL).
- Cobertura extendida sin trabajo adicional: KMS sign/verify, Cognito, Step Functions, EventBridge, etc., quedan disponibles para futuros PoCs.

### Negativo
- Pérdida de fidelidad MinIO-as-real-S3 para multipart > 100 MB y patterns muy específicos de S3 versioning de gran escala. Aceptable: este lab no ejercita esos paths.
- Dependencia de un proyecto joven (Floci): si el upstream desaparece, hay que migrar. Mitigación: licencia MIT permite fork, y Floci respeta el wire protocol AWS — los adapters Java no necesitan cambiar si volvemos a LocalStack Pro o moto-server en el futuro.
- OpenBao desaparece: cualquier secret que hoy use `bao kv` directamente (sin pasar por AWS Secrets Manager API) debe re-trabajarse. Auditado: el único consumo era seeding en init jobs, no hay código de producción que use el cliente Vault.

### Mitigaciones
- ADR-0005, ADR-0028, ADR-0029, ADR-0033 quedan **superseded-by 0042** (no se borran — preservan historia).
- `FLOCI_ENDPOINT` se lee con fallback chain: explicit `endpointOverride` → env `FLOCI_ENDPOINT` → AWS default. Si Floci se quita, los adapters siguen funcionando contra AWS real.
- Healthcheck del compose usa `GET /_floci/health` (verificado contra `Dockerfile.native` del source).
- Adapters Java consolidan en una utilidad única que devuelve `Optional<URI>` para `endpointOverride`, evitando duplicación.

## Validación

- `docker compose up -d` en `compose/` arranca 1 contenedor Floci + 1 init job (vs. 4 + 4 antes).
- `aws --endpoint-url http://localhost:4566 s3 ls` lista los buckets seedeados (`risk-events`, `risk-models`).
- `aws --endpoint-url http://localhost:4566 sqs list-queues` lista las colas (`risk-decisions`, `risk-decisions-dlq`, `risk-audit-queue`).
- `aws --endpoint-url http://localhost:4566 secretsmanager list-secrets` lista los secrets seedeados.
- Tests de integración en `tests/integration/` corren contra `FlociContainer` (Testcontainers) y pasan los mismos casos que antes corrían contra `MotoContainer + MinioContainer + OpenBaoContainer`.

## Relacionado

- [[0005-aws-mocks-stack]] (superseded)
- [[0028-minio-agpl-acceptable]] (superseded — MinIO ya no se usa)
- [[0029-openbao-vs-vault]] (superseded — OpenBao removido del stack)
- [[0033-moto-inline-vs-localstack]] (superseded — Moto removido)
- Docs: `docs/10-aws-mocks-locales.md` (reescrito en este cambio)
- Skill: `.ai/primitives/skills/add-mock-aws-service.md` (reescrito en este cambio)

## Referencias

- Floci: https://github.com/floci-io/floci
- LocalStack sunset (marzo 2026): https://blog.localstack.cloud/the-road-ahead-for-localstack/
- Floci Testcontainers Java: https://github.com/floci-io/testcontainers-floci (Maven Central: `io.floci:testcontainers-floci:1.4.0`)
- Health endpoint: `GET /_floci/health` (`/tmp/floci-audit/floci/docker/Dockerfile.native`)
- Image: `floci/floci:latest` (GraalVM native, MIT)
