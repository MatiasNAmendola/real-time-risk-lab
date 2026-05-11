# 10 — AWS mocks locales (2026)

## Contexto

Para correr una PoC que use SDKs de AWS (S3, SQS, SNS, Secrets Manager, KMS, DynamoDB, etc.) sin tocar cuentas reales, en 2026 hay esencialmente tres caminos:

1. **LocalStack Community Edition** — La opción histórica. **Sunset en marzo 2026**: requiere auth-token, sin más security updates en la rama Community. Ver [blog.localstack.cloud](https://blog.localstack.cloud/the-road-ahead-for-localstack/). Inviable para flujos offline / CI sin credenciales externas.
2. **LocalStack Pro** — Suscripción de pago. Fuera de scope para un lab.
3. **Floci** — Emulador AWS unificado, **MIT**, drop-in replacement de LocalStack, sin auth-token, sin feature gates. Es lo que usa este repo desde ADR-0042.

Este repo usa **Floci** como única herramienta de mock AWS. Toda la pila previa (Moto + MinIO + ElasticMQ + OpenBao) fue retirada — la decisión y consecuencias están en [ADR-0042](../vault/02-Decisions/0042-floci-unified-aws-emulator.md).

## Floci en una página

| Aspecto | Valor |
|---|---|
| Imagen | `floci/floci:latest` (GraalVM native binary) |
| Tamaño | ~40 MB |
| Cold start | ~24 ms |
| RAM idle | ~13 MiB |
| Endpoint único | `:4566` (mismo puerto que LocalStack) |
| Health endpoint | `GET /_floci/health` |
| Licencia | MIT |
| Cobertura | 46 servicios AWS (S3, SQS, SNS, Secrets, KMS, STS, IAM, DynamoDB, Cognito, Kinesis, Step Functions, EventBridge, API Gateway, Lambda, MSK, RDS, EKS, ECS, EC2, etc.) |

LocalStack-parity: los endpoints `/_localstack/health` y `/_localstack/init` siguen funcionando, y `LOCALSTACK_*` env vars se traducen automáticamente a `FLOCI_*`. Útil si migrás scripts existentes — no hay que reescribirlos.

## Configuración local (compose)

```yaml
# compose/docker-compose.yml — extracto
services:
  floci:
    image: floci/floci:latest
    ports:
      - "4566:4566"
    environment:
      FLOCI_HOSTNAME: floci                 # URLs internas usan http://floci:4566
      FLOCI_BASE_URL: http://floci:4566
      FLOCI_DEFAULT_REGION: us-east-1
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:4566/_floci/health || exit 1"]
```

Seeding (S3 buckets + SQS queues + SNS topics + secrets) con un solo init container `amazon/aws-cli`:

```bash
EP=http://floci:4566
aws --endpoint-url=$EP s3 mb s3://risk-events
aws --endpoint-url=$EP sqs create-queue --queue-name risk-decisions
aws --endpoint-url=$EP sns create-topic --name risk-events
aws --endpoint-url=$EP secretsmanager create-secret --name risk-engine/db-password --secret-string "$POC_DB_PASSWORD"
```

Variables a setear en la app:

```bash
FLOCI_ENDPOINT=http://floci:4566       # único endpoint
AWS_ENDPOINT_URL=http://floci:4566     # equivalente AWS-standard
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
```

## Configuración en código (Java AWS SDK v2)

Todos los adapters del repo usan una utilidad `FlociEndpoint` que lee `FLOCI_ENDPOINT` → `AWS_ENDPOINT_URL` → legacy `AWS_ENDPOINT_URL_S3/_SQS/_SECRETSMANAGER` → vacío (defaultea a AWS real). Ejemplo:

```java
Optional<URI> endpoint = FlociEndpoint.resolve("AWS_ENDPOINT_URL_S3");
S3Client s3 = S3Client.builder()
    .endpointOverride(endpoint.orElse(null))    // ó .applyMutation para hacerlo opcional
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .forcePathStyle(true)
    .build();
```

`forcePathStyle(true)` es necesario para S3 porque Floci sirve buckets vía path-style, igual que MinIO/LocalStack.

## Configuración en Kubernetes (k3d local)

```yaml
# poc/k8s-local/addons/70-aws-mocks.yaml — extracto
apiVersion: apps/v1
kind: Deployment
metadata:
  name: floci
  namespace: aws-mocks
spec:
  template:
    spec:
      containers:
        - name: floci
          image: floci/floci:latest
          ports: [ { containerPort: 4566 } ]
          readinessProbe:
            httpGet: { path: /_floci/health, port: 4566 }
```

Service: `floci.aws-mocks.svc.cluster.local:4566`. El init job (`71-aws-mocks-init.yaml`) seedea los mismos recursos que el compose-init.

## Las 3 capas de testing

Este modelo no cambia con Floci — es la misma separación que tenía la pila per-servicio. Solo se simplifica el setup de cada capa.

### Capa 1 — Unit tests (sin red)
- No usan Floci. Mocks de los puertos (Mockito o test doubles a mano).
- Verifican lógica de dominio + adapters contra interfaces falsas.

### Capa 2 — Integration tests (Testcontainers)
- Levantan un `FlociContainer` (Testcontainers) por test class.
- Imagen: `floci/floci:latest`. Health probe en `/_floci/health:4566`.
- Ubicación: `tests/integration/src/test/java/.../containers/FlociContainer.java`.
- Cada test cubre **una** interacción AWS-SDK→Floci (Secrets Manager, S3, SQS, SNS, etc.).
- Ejemplo: `FlociSecretsManagerIntegrationTest`, `AuditEventS3IntegrationTest`.

### Capa 3 — E2E tests (compose stack completo)
- Levantan compose con Floci + Postgres + Redpanda + OTel Collector + las apps.
- Ejecutan flujos completos: POST /decision → audit en S3 → mensaje en SQS → trace en OTel.
- Ubicación: `tests/integration/src/test/java/.../e2e/RiskDecisionE2EIntegrationTest.java`.

## Migración desde la pila anterior (referencia rápida)

| Antes (Moto + MinIO + ElasticMQ + OpenBao) | Ahora (Floci) |
|---|---|
| `AWS_ENDPOINT_URL_S3=http://minio:9000` | `FLOCI_ENDPOINT=http://floci:4566` |
| `AWS_ENDPOINT_URL_SQS=http://elasticmq:9324` | mismo `FLOCI_ENDPOINT` |
| `AWS_ENDPOINT_URL_SECRETSMANAGER=http://moto:5000` | mismo `FLOCI_ENDPOINT` |
| `OPENBAO_URL=http://openbao:8200` + `OPENBAO_TOKEN=root` | (no aplica — Secrets Manager API en Floci) |
| `minio-init` (mc) + `elasticmq-init` (aws) + `moto-init` (boto3) + `openbao-init` (bao) | un solo `floci-init` (aws CLI) |
| 4 contenedores en compose / k8s | 1 contenedor |
| 4 healthchecks distintos | 1 healthcheck `/_floci/health` |
| Licencias mezcladas: Apache 2.0 + AGPL-3.0 + MPL-2.0 + Apache 2.0 | MIT |

## Referencias

- [ADR-0042: Floci as Unified AWS Emulator](../vault/02-Decisions/0042-floci-unified-aws-emulator.md)
- [Floci docs](https://floci.io/floci/)
- [Floci Testcontainers Java](https://github.com/floci-io/testcontainers-floci) (este repo usa un wrapper custom en `tests/integration`, no la dependencia)
- [LocalStack sunset, March 2026](https://blog.localstack.cloud/the-road-ahead-for-localstack/)
- Skill: `.ai/primitives/skills/add-mock-aws-service.md`
