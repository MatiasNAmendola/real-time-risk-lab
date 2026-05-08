# 10 — AWS mocks locales (2025-2026)

## Contexto

Para correr una PoC que use SDKs de AWS (S3, SQS, SNS, Secrets Manager, KMS, DynamoDB) sin tocar cuentas reales, hay dos caminos: LocalStack o un stack de tools dedicados. A 2026-05-07 hay incertidumbre sobre la licencia de LocalStack Community — un agente de research reportó que fue discontinuada en marzo 2026, pero parte de las fuentes citadas eran sospechosamente específicas y no verificadas. Antes de descartar LocalStack, abrir [blog.localstack.cloud](https://blog.localstack.cloud/) y confirmar el estado actual.

Independientemente de qué pase con LocalStack, conviene conocer el stack alternativo "por servicio dedicado" — es más fiel a producción, sin riesgo de licencia, y architecturally more interesting from a design standpoint.

## Stack recomendado (verificado, sin alucinación)

| Tool | Para qué servicio | Imagen | Puerto | Licencia | Estado |
|---|---|---|---|---|---|
| Moto / motoserver | SNS, Secrets Manager, KMS, IAM/STS, fallback genérico | `motoserver/moto:latest` | 5000 | Apache 2.0 | Maduro, activo |
| MinIO | S3 (compatible, prod-grade) | `minio/minio:latest` | 9000 / 9001 | AGPL-3.0 | Muy activo |
| ElasticMQ | SQS | `softwaremill/elasticmq-native:latest` | 9324 / 9325 | Apache 2.0 | Activo |
| OpenBao | Secrets Manager + KMS (transit engine) | `openbao/openbao:latest` | 8200 | MPL-2.0 | Fork de Vault mantenido por Linux Foundation |
| DynamoDB Local | DynamoDB oficial AWS | `amazon/dynamodb-local:latest` | 8000 | Propietario AWS, gratis | Mantenido por AWS |
| Lambda RIE | Lambda local oficial | embebible en imagen Lambda | — | Apache 2.0 | Mantenido por AWS |

Atención con MinIO: licencia AGPL-3.0. Para uso interno o desarrollo no hay problema. Si el producto final es SaaS y embebés MinIO, conviene revisar con legal.

## Cuándo cada cosa

### PoC mínima viable
Un solo contenedor: `motoserver/moto:latest` en `:5000`. Cubre casi todos los servicios AWS en un solo endpoint. Variable a setear:

```bash
AWS_ENDPOINT_URL=http://moto:5000
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
```

Pros: simplicidad máxima, un solo proceso.
Contras: fidelidad limitada en algunos servicios (S3 con cargas grandes, SQS con semánticas finas).

### Stack production-realistic
Un contenedor por servicio. Cada tool es el mejor en su nicho:

```yaml
moto:        motoserver/moto:latest             :5000   # SNS, Secrets fallback, IAM/STS
minio:       minio/minio:latest                 :9000   # S3 prod-grade
elasticmq:   softwaremill/elasticmq-native      :9324   # SQS fiel
openbao:     openbao/openbao:latest             :8200   # Secrets + KMS (transit)
dynamodb:    amazon/dynamodb-local:latest       :8000   # DynamoDB oficial
```

Pros: cada servicio se comporta como prod (mismas APIs, mismos errores, mismas latencias relativas).
Contras: 5 contenedores, más memoria, más wiring.

## Mapeo a endpoint-overrides AWS SDK

```bash
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test

AWS_ENDPOINT_URL_S3=http://minio:9000
AWS_ENDPOINT_URL_SQS=http://elasticmq:9324
AWS_ENDPOINT_URL_SNS=http://moto:5000
AWS_ENDPOINT_URL_SECRETSMANAGER=http://moto:5000     # o openbao para mayor fidelidad
AWS_ENDPOINT_URL_KMS=http://moto:5000                # o openbao transit engine
AWS_ENDPOINT_URL_IAM=http://moto:5000
AWS_ENDPOINT_URL_DYNAMODB=http://dynamodb:8000
```

AWS SDK v2 (Java, Go, Python recientes) respeta `AWS_ENDPOINT_URL_<SERVICE>` por servicio sin tocar código. SDK v1 requiere builders custom — uno más para argumentar la migración a v2.

## Wiring en k8s-local

El cluster local ya replica patrones de infraestructura enterprise. El stack de mocks AWS se agrega como un addon mas:

```
poc/k8s-local/addons/
├── 70-aws-mocks.yaml        # ns aws-mocks + Deployments + Services
└── 71-aws-mocks-init.yaml   # Job que crea bucket S3, queue SQS, secrets en Moto/OpenBao
```

`poc/k8s-local/scripts/demo.sh` expone:
- MinIO console: `http://localhost:9001` (${MINIO_ROOT_USER} / ${MINIO_ROOT_PASSWORD})
- ElasticMQ UI: `http://localhost:9325`
- Moto API: `http://localhost:5000` (sin UI, solo REST)
- OpenBao UI: `http://localhost:8200/ui` (token: root en dev mode)
- DynamoDB Local: `http://localhost:8000` (sin UI)

La app `risk-engine` recibe los endpoint-overrides como env vars en el Deployment template.

## Technical Talking Points

**"¿Cómo testeás contra AWS sin gastar?"**
Se separa el concepto en tres niveles:

1. **Unit tests**: mocks puros del SDK (Mockito, gomock, moto en modo decorator) — rápidos, sin red.
2. **Integration tests**: contenedores dedicados por servicio (MinIO, ElasticMQ, DynamoDB Local) o Moto server — fidelidad alta de API, sin tocar AWS real, corren en CI.
3. **E2E / staging**: cuenta AWS de staging real — última línea de validación, siempre antes de prod.

> "Los unit tests me dan velocidad; los integration tests con tools dedicados me dan fidelidad de protocolo; staging me da fidelidad operacional. Las tres capas no se reemplazan, se complementan."

**"¿Por qué no LocalStack?"**
Hasta 2025 era la opción default. En 2026 el modelo de licencia cambió y conviene revalidar. Independientemente, el stack por-servicio es más fiel a producción — MinIO se comporta como S3 real porque ES un object store real, no un mock.

**"¿Cómo simulás Secrets Manager + IRSA en local?"**
Localmente no hay AWS, así que IRSA no aplica. Se usan dos tácticas:
- Para secrets: External Secrets Operator con provider `kubernetes` apuntando a un secret en otro namespace. En prod el provider cambia a `aws` y todo lo demás queda igual.
- Para IAM roles: ServiceAccount + RoleBinding plano — no es IRSA real, pero el shape del RBAC es el mismo.

> "El truco no es replicar AWS bit-a-bit, es replicar el shape del contrato. La diferencia entre dev y prod debería ser una variable de entorno y un provider, no un refactor."

**"¿Qué hay del costo de mantener este stack?"**
Bajo: imágenes Docker oficiales, Helm charts, sin licencias enterprise. El costo real está en:
- Mantener los datos seed sincronizados con prod (buckets, queues, secrets esperados).
- CI: arrancar y tirar contenedores agrega segundos por test suite.
- Drift: si prod actualiza una API, los mocks pueden quedar atrasados.

Mitigación: tests de contrato contra AWS real corren en pipeline nightly o pre-deploy, no en cada PR.

## Frases-llave

> "AWS local no es replicar bit-a-bit, es replicar el shape del contrato."

> "MinIO no es un mock, es un object store real con API S3 — es la diferencia entre simular y reemplazar."

> "Moto cubre el 90% en un contenedor; los servicios donde la fidelidad importa, los mando a la herramienta dedicada."

## Wired to apps

Los mocks están efectivamente wired a las dos PoCs Java:

### Tabla de uso

| PoC | App | Servicio AWS | Mock | Adapter |
|---|---|---|---|---|
| vertx-layer-as-pod-eventbus | usecase-app | S3 audit log | MinIO :9000 | `S3AuditPublisher` — publica cada decisión |
| vertx-layer-as-pod-eventbus | usecase-app | SQS output alternativo | ElasticMQ :9324 | `SqsDecisionPublisher` — dual output con Kafka |
| vertx-layer-as-pod-eventbus | repository-app | Secrets Manager (DB password) | Moto :5000 + OpenBao :8200 | `SecretsBootstrap.resolveDbPassword()` |
| vertx-layer-as-pod-eventbus | consumer-app | S3 audit DECLINE/REVIEW | MinIO :9000 | `ConsumerS3AuditPublisher` |
| no-vertx-clean-engine | (todos) | S3 audit | MinIO (Phase 2) | Port `AuditEventPublisher` creado; NoOp activo |
| no-vertx-clean-engine | (todos) | Secrets Manager | Moto (Phase 2) | Port `SecretsProvider` creado; EnvSecretsProvider activo |

### Degradación graceful

Todos los adapters están diseñados para no fallar si el mock no está disponible:
- `S3AuditPublisher` / `ConsumerS3AuditPublisher`: si `AWS_ENDPOINT_URL_S3` no está seteado, no publican nada.
- `SqsDecisionPublisher`: si `AWS_ENDPOINT_URL_SQS` no está seteado, no publica nada.
- `SecretsBootstrap`: si Moto falla, intenta OpenBao; si OpenBao falla, usa `PG_PASSWORD` env var.
- `no-vertx-clean-engine`: usa `NoOpAuditEventPublisher` y `EnvSecretsProvider` hasta Phase 2 (Gradle).

### E2E — hacer una decisión y verificar audit log

```bash
# Levantar el stack completo (incluye MinIO, Moto, ElasticMQ, OpenBao)
./poc/vertx-layer-as-pod-eventbus/scripts/up.sh

# Hacer una decisión de alto riesgo
curl -s -X POST http://localhost:8080/risk \
  -H 'Content-Type: application/json' \
  -d '{"transactionId":"tx-audit-e2e","customerId":"c-4","amountCents":200000}'

# Verificar audit log en MinIO (publicado por usecase-app)
aws --endpoint-url http://localhost:9000 \
  --no-sign-request \
  s3 ls s3://risk-audit/risk-audit/2026/05/07/

# Verificar audit de alto riesgo del consumer
aws --endpoint-url http://localhost:9000 \
  --no-sign-request \
  s3 ls s3://risk-audit/risk-audit/consumer/2026/05/07/

# Verificar secret leído desde Moto al startup de repository-app
# (ver logs del container)
docker compose logs repository-app | grep SecretsBootstrap
# Output esperado:
# [repository-app] SecretsBootstrap: loaded secret 'riskplatform/db-password' from Moto Secrets Manager at http://moto:5000
```

## Riesgos y trampas

- **AGPL de MinIO**: para SaaS revisar con legal. Para dev/CI no aplica.
- **Drift de APIs**: AWS evoluciona, los mocks van atrás. Fallback: tests de contrato nightly contra AWS real.
- **Datos no persistentes**: en la PoC todo es `emptyDir`. Para tests que requieran estado, montar volume o reseed antes de cada run.
- **No reemplaza staging**: este stack es para desarrollo local y CI. Antes de prod siempre pasar por staging real.
