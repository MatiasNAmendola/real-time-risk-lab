---
name: add-mock-aws-service
intent: Agregar un servicio AWS mockeado localmente para desarrollo sin cuenta AWS
inputs: [aws_service, mock_tool, port, configuration]
preconditions:
  - docker-compose.yml en el PoC o poc/k8s-local disponible
postconditions:
  - Servicio mock corriendo con la misma API que AWS
  - Aplicacion Java configurada para apuntar al endpoint local
  - Test de integracion verifica la interaccion con el mock
related_rules: [secrets-handling, containers-docker, licensing]
---

# Skill: add-mock-aws-service

## Mocks disponibles

| Servicio AWS | Mock local | Licencia | Puerto default |
|---|---|---|---|
| S3, SQS, SNS, DynamoDB, IAM | Moto (Python) | Apache 2.0 | 5000 |
| S3 | MinIO | AGPL 3.0 | 9000 |
| SQS | ElasticMQ | Apache 2.0 | 9324 |
| Secrets Manager, KV | OpenBao | MPL 2.0 | 8200 |

## Agregar Moto (multi-servicio)

En `docker-compose.yml`:
```yaml
services:
  moto:
    image: motoserver/moto:latest
    ports:
      - "5000:5000"
    environment:
      - MOTO_PORT=5000
```

Configurar en Java (AWS SDK v2):
```java
S3Client s3 = S3Client.builder()
    .endpointOverride(URI.create("http://localhost:5000"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();
```

## Agregar MinIO (S3-compatible)

```yaml
services:
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-change-me-minio-user}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_USER:-change-me-minio-user}
    command: server /data --console-address ":9001"
```

Licencia AGPL: solo usar para desarrollo/testing. No redistribuir como producto.

## Agregar OpenBao (Secrets Manager)

```yaml
services:
  openbao:
    image: openbao/openbao:latest
    ports:
      - "8200:8200"
    environment:
      BAO_DEV_ROOT_TOKEN_ID: root
    command: server -dev
```

En la app: apuntar `VAULT_ADDR=http://localhost:8200`, `VAULT_TOKEN=root`.

## Notas
- En k8s-local: los mocks ya estan en `poc/k8s-local/addons/70-aws-mocks.yaml`.
- Nunca hardcodear credenciales reales. Mock credentials: `test/test` o `${MINIO_ROOT_USER:-change-me-minio-user}/${MINIO_ROOT_USER:-change-me-minio-user}` son seguros para dev.
- Para External Secrets Operator en k8s: hay un `SecretStore` de tipo `vault` apuntando a OpenBao.
