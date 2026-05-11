---
name: add-mock-aws-service
intent: Agregar o seedear un servicio AWS mockeado localmente para desarrollo sin cuenta AWS
inputs: [aws_service, resource_name, configuration]
preconditions:
  - compose/docker-compose.yml o poc/k8s-local/addons/70-aws-mocks.yaml disponibles con Floci corriendo
postconditions:
  - Recurso AWS (bucket / queue / topic / secret / kms-key) creado en Floci
  - Aplicacion Java configurada para apuntar al endpoint FLOCI_ENDPOINT
  - Test de integracion verifica la interaccion contra FlociContainer (Testcontainers)
related_rules: [secrets-handling, containers-docker, licensing, observability-otel]
related_adrs: [0042]
---

# Skill: add-mock-aws-service

## Contexto (vigente desde ADR-0042)

Este lab usa **Floci** (https://github.com/floci-io/floci) como emulador AWS unificado. **Un solo contenedor**, **un solo endpoint** (`http://floci:4566`), licencia MIT. Reemplaza la pila previa MinIO + ElasticMQ + Moto + OpenBao.

| Servicio AWS | Cómo lo expone Floci | Notas |
|---|---|---|
| S3 | In-process en :4566 | Versioning, multipart, pre-signed URLs, Object Lock |
| SQS | In-process en :4566 | Standard + FIFO, DLQ, visibility timeout |
| SNS | In-process en :4566 | Topics, subscriptions, SQS/Lambda/HTTP delivery |
| Secrets Manager | In-process en :4566 | Versioning, resource policies, tagging |
| KMS | In-process en :4566 | Encrypt/decrypt, sign/verify, data keys, aliases |
| STS | In-process en :4566 | AssumeRole, GetSessionToken, WebIdentity |
| IAM | In-process en :4566 | Users, roles, groups, policies |
| DynamoDB | In-process en :4566 | GSI/LSI, Query, Scan, TTL, transactions |

Image: `floci/floci:latest` (GraalVM native, ~40 MB, ~24 ms startup, ~13 MiB idle).
Health endpoint: `GET /_floci/health` en puerto 4566.

## 1. Configurar Floci en compose (ya está, referencia)

```yaml
# compose/docker-compose.yml
services:
  floci:
    image: floci/floci:latest
    networks: [data-net]
    ports:
      - "4566:4566"
    environment:
      FLOCI_HOSTNAME: floci                       # URLs internas usan http://floci:4566
      FLOCI_BASE_URL: http://floci:4566
      FLOCI_DEFAULT_REGION: us-east-1
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:4566/_floci/health || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 20
      start_period: 10s
    mem_limit: 256m
    cpus: "0.50"
```

En k8s: `poc/k8s-local/addons/70-aws-mocks.yaml` — un Deployment + Service en namespace `aws-mocks`, accesible como `http://floci.aws-mocks.svc.cluster.local:4566`.

## 2. Seeding via aws CLI (recipe único)

Usar imagen `amazon/aws-cli:latest`. Credenciales dummy (`test`/`test`). Endpoint único `FLOCI_ENDPOINT=http://floci:4566`.

```yaml
# compose/docker-compose.yml — servicio floci-init
floci-init:
  image: amazon/aws-cli:latest
  networks: [data-net]
  depends_on:
    floci:
      condition: service_healthy
  environment:
    AWS_ACCESS_KEY_ID:     test
    AWS_SECRET_ACCESS_KEY: test
    AWS_DEFAULT_REGION:    us-east-1
    FLOCI_ENDPOINT:        http://floci:4566
    POC_DB_PASSWORD:       ${POC_DB_PASSWORD:-change-me-db-password}
    POC_API_KEY:           ${POC_API_KEY:-change-me-api-key}
  entrypoint: /bin/sh
  command:
    - -ec
    - |
      EP="$$FLOCI_ENDPOINT"
      # S3 buckets
      for b in risk-events risk-models risk-audit; do
        aws --endpoint-url=$$EP s3 mb s3://$$b 2>/dev/null || true
      done
      # SQS queues (DLQ first, then main with redrive)
      aws --endpoint-url=$$EP sqs create-queue --queue-name risk-decisions-dlq >/dev/null 2>&1 || true
      aws --endpoint-url=$$EP sqs create-queue --queue-name risk-decisions     >/dev/null 2>&1 || true
      aws --endpoint-url=$$EP sqs create-queue --queue-name risk-audit-queue   >/dev/null 2>&1 || true
      # SNS topic
      aws --endpoint-url=$$EP sns create-topic --name risk-events >/dev/null 2>&1 || true
      # Secrets Manager
      aws --endpoint-url=$$EP secretsmanager create-secret \
        --name risk-engine/db-password --secret-string "$$POC_DB_PASSWORD" >/dev/null 2>&1 || true
      aws --endpoint-url=$$EP secretsmanager create-secret \
        --name risk-engine/api-key --secret-string "$$POC_API_KEY" >/dev/null 2>&1 || true
      echo "Floci seeded."
  restart: "no"
```

## 3. Configurar el cliente Java (AWS SDK v2)

Todos los clients leen `FLOCI_ENDPOINT` con fallback chain. Usar la utility centralizada:

```java
import io.riskplatform.shared.aws.FlociEndpoint;  // o equivalente per-poc

S3Client s3 = S3Client.builder()
    .applyMutation(FlociEndpoint::applyTo)        // setea endpointOverride si FLOCI_ENDPOINT está definido
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .forcePathStyle(true)                          // requerido para S3 in-process
    .build();
```

Fallback chain (en `FlociEndpoint`):
1. Si pasaron `endpointOverride` explícito → respetarlo.
2. Si `FLOCI_ENDPOINT` env está set → usarlo.
3. Si no → AWS default (talks to real AWS si las credenciales son reales).

## 4. Agregar un test de integración con Testcontainers

Usar `FlociContainer` (custom wrapper en `tests/integration/src/test/java/io/riskplatform/integration/containers/FlociContainer.java`):

```java
@Testcontainers
class S3IntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();  // exposes 4566

    @Test
    void shouldCreateBucket() {
        S3Client s3 = S3Client.builder()
            .endpointOverride(URI.create(floci.getEndpoint()))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .forcePathStyle(true)
            .build();
        s3.createBucket(b -> b.bucket("my-bucket"));
        assertThat(s3.listBuckets().buckets())
            .anyMatch(b -> b.name().equals("my-bucket"));
    }
}
```

Alternativa "oficial": `io.floci:testcontainers-floci:1.4.0` en Maven Central. Este lab usa el wrapper custom para no agregar otra dependencia.

## Notas

- **Credenciales mock**: siempre `test/test` para Floci. Si el `AWS_ACCESS_KEY_ID` tiene 12 dígitos, Floci lo usa como account ID (multi-account isolation, ver README de Floci).
- **OpenBao se eliminó** (ADR-0029 superseded). Si necesitás secrets, usá AWS Secrets Manager API contra Floci, no `bao kv`.
- **MinIO se eliminó** (ADR-0028 superseded). Si necesitás S3 real (no probable en este lab), levantá MinIO standalone fuera del stack.
- **Moto se eliminó** (ADR-0033 superseded). Los tests inline con `@mock_aws` siguen siendo válidos para tests Python aislados, pero el stack de servicios usa Floci.
- En k8s: el job `floci-init` (`poc/k8s-local/addons/71-aws-mocks-init.yaml`) seedea los mismos recursos al levantar el cluster.
- Para External Secrets Operator: usar el provider `aws/SecretsManager` apuntando a `http://floci.aws-mocks.svc.cluster.local:4566`.

## Referencias

- ADR-0042: `vault/02-Decisions/0042-floci-unified-aws-emulator.md`
- Floci docs: https://floci.io/floci/
- Floci Testcontainers: https://github.com/floci-io/testcontainers-floci
