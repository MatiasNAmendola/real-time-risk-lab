# k8s-local — Real-Time Risk Lab PoC: Replica local de mega-infra

Replica local de los patrones de infraestructura de mega-infra, adaptada para correr
en macOS con k3d (k3s en Docker) sin dependencias de AWS. Permite demostrar en una
review los mismos patrones de plataforma que existen en producción.

---

## Que es esto

- ArgoCD con GitOps sync automatico + AppProject scoped.
- Argo Rollouts con canary analysis via OpenObserve HTTP API (success-rate + latency-p99).
- OpenObserve standalone como unica fuente de observability: logs + metrics + traces + alerting (~150 MB).
- External Secrets Operator con provider `kubernetes` (simula AWS Secrets Manager).
-  Tansu 1 broker sin TLS (topologia in-cluster equivalente a producción).
- Traefik (built-in en k3d) como reemplazo de ALB + ACM.
- Chart manual `risk-engine` con Rollout y ExternalSecret.

---

## Provider de Kubernetes

| Provider | Pro | Contra |
|---|---|---|
| OrbStack (default si esta instalado) | <10s arranque, LoadBalancer real (198.19.x.x), integracion Mac nativa | Mac only, requiere OrbStack instalado |
| k3d (fallback) | Cross-platform, cluster aislado, facil de wipear con `down.sh` | Pull inicial 30-60s, LoadBalancer simulado via port-forward |

Default: autodetect. Si `orb` esta en PATH y `orb status` responde → OrbStack. Sino → k3d.

Forzar provider:

```bash
# Via variable de entorno
K8S_PROVIDER=orbstack ./scripts/up.sh
K8S_PROVIDER=k3d      ./scripts/up.sh

# Via flag CLI
./scripts/up.sh --provider orbstack
./scripts/up.sh --provider k3d
```

Comportamiento de `down.sh` segun provider:

```bash
# OrbStack: borra solo los namespaces del PoC (k8s sigue corriendo para otras apps)
./scripts/down.sh

# OrbStack + deshabilitar k8s completamente
./scripts/down.sh --full

# k3d: borra el cluster completo
./scripts/down.sh
```

---

## Pre-requisitos

```bash
# Para k3d (default si OrbStack no esta instalado)
brew install k3d helm kubectl

# Para OrbStack
# Instalar desde https://orbstack.dev
brew install helm kubectl

# Verificar versiones minimas:
k3d version    # >= 5.x  (solo si usas k3d)
helm version   # >= 3.x
kubectl version --client
```

Docker Desktop (o Colima/OrbStack) debe estar corriendo.

---

## Levantarlo

```bash
cd poc/k8s-local
./scripts/up.sh
```

Primera corrida: ~3-5 minutos (pulls de imagenes). Corridas subsiguientes: ~1 min.
El script es idempotente — si el cluster ya existe, lo saltea y solo aplica cambios.

Al finalizar imprime la password inicial de ArgoCD.

---

## Ver URLs y port-forwards

```bash
./scripts/demo.sh
```

Copia cada comando en una terminal separada. Ejemplo:

```
ArgoCD       → kubectl -n argocd port-forward svc/argocd-server 8081:80
                 URL: https://localhost:8081  (admin / <password impresa por up.sh>)

OpenObserve  → kubectl -n openobserve port-forward svc/openobserve 5080:5080
                 URL: http://localhost:5080  (root@example.com / ${OPENOBSERVE_PASSWORD:-change-me-openobserve-local})

 Tansu       → kubectl -n tansu port-forward svc/tansu 9092:9092
                 No bundled UI; use: kafka-topics --bootstrap-server localhost:9092 --list

Rollouts UI  → kubectl -n argo-rollouts port-forward svc/argo-rollouts-dashboard 3100:3100
                 URL: http://localhost:3100

Risk Engine  → kubectl -n risk port-forward svc/risk-engine 8090:8080
                 URL: http://localhost:8090/risk
```

---

## Mapeo producción → local PoC

| Mega prod | Local PoC | Razon |
|---|---|---|
| EKS Auto Mode + Karpenter | k3d o OrbStack k8s | k3s/OrbStack corren localmente sin AWS |
| ALB Ingress Controller + ACM | Traefik (built-in en k3d) / LoadBalancer real en OrbStack | k3d incluye Traefik; OrbStack da IPs 198.19.x.x reales |
| AWS Secrets Manager + ESO + IRSA | ESO + provider `kubernetes` (PoC) / Moto (variante AWS-realista) | `ClusterSecretStore` apunta a `secrets-source`; en prod, cambiar provider a `aws` |
| S3 | MinIO (`aws-mocks` namespace) | S3-API compatible, Apache 2.0 |
| SQS + DLQ | ElasticMQ (`aws-mocks` namespace) | SQS-API compatible con DLQ support |
| SNS / KMS / DynamoDB | Moto server (`aws-mocks` namespace) | Misma base de tests de botocore/moto |
| Vault / KMS alternativo | OpenBao (`aws-mocks` namespace) | Fork comunitario de Vault, API identica, MPL 2.0 |
| MSK /  Tansu in-cluster multi-broker |  Tansu operator 1 broker, sin TLS | Misma topologia, minimizada para PoC local |
| OTEL backend SaaS (Axiom u otro) | OpenObserve standalone | OTLP-compatible, autohospedado, sin costo |
| ArgoCD + GitLab webhook | ArgoCD sync automatico local | `repoURL` puede apuntar a path local o Git remoto |
| GitLab CI | (fuera de scope) | No aplica localmente |
| kube-prometheus-stack | OpenObserve standalone | ~150 MB vs ~1.5 GB; cubre logs + metrics + traces + alerting en un solo binario. Ver vault/02-Decisions/0045-observability-stack-local.md |
| Argo Rollouts canary + Prometheus analysis | Argo Rollouts canary + OpenObserve `web` provider | AnalysisTemplates usan HTTP GET a la API SQL de OpenObserve en lugar de PromQL |
| IRSA (IAM Roles for Service Accounts) | ServiceAccount + RoleBinding plano | Sin AWS STS; ESO usa SA con RoleBinding a `secrets-source` |

---

## Usar la imagen real de la PoC Vert.x

Una vez que `poc/vertx-layer-as-pod-eventbus/` tenga un Dockerfile listo:

```bash
# 1. Build la imagen
docker build -t risk-engine:dev ../../poc/vertx-layer-as-pod-eventbus/

# 2. Importarla al cluster k3d (no necesita registry externo)
k3d image import risk-engine:dev -c naranja-poc

# 3. Actualizar el Rollout para disparar un canary
kubectl -n risk set image rollout/risk-engine risk-engine=risk-engine:dev

# 4. Observar el progreso del canary
kubectl argo rollouts -n risk get rollout risk-engine --watch
```

La imagen placeholder en `values.yaml` es `localhost:5000/risk-engine:dev`.

---

## Mocks AWS

El stack corre en el namespace `aws-mocks`. No hay persistencia — todo en `emptyDir`. Suficiente para una PoC y demos.

| Servicio AWS | Tool | Imagen | Puerto interno |
|---|---|---|---|
| S3 | MinIO | `minio/minio:latest` | 9000 (API), 9001 (console) |
| SQS | ElasticMQ | `softwaremill/elasticmq-native:latest` | 9324 (API), 9325 (UI) |
| SNS + Secrets Manager + KMS + DynamoDB | Moto | `motoserver/moto:latest` | 5000 |
| Secrets Manager / KV (Vault-compatible) | OpenBao | `openbao/openbao:latest` | 8200 |

El Job `aws-mocks-init` crea automaticamente al inicio:

- Buckets MinIO: `risk-events`, `risk-models`
- Queues ElasticMQ: `risk-decisions`, `risk-decisions-dlq`
- SNS topic Moto: `risk-events`
- Secrets Moto: `risk-engine/db-password`, `risk-engine/api-key`
- Secrets OpenBao: `secret/risk-engine/db`, `secret/risk-engine/keys`

### Comandos de ejemplo (con port-forwards activos)

```bash
# Subir un objeto a "S3" (MinIO)
aws --endpoint-url http://localhost:9000 \
    s3 cp test.txt s3://risk-events/

# Listar buckets
aws --endpoint-url http://localhost:9000 s3 ls

# Mandar mensaje a "SQS" (ElasticMQ)
aws --endpoint-url http://localhost:9324 sqs send-message \
  --queue-url http://localhost:9324/queue/risk-decisions \
  --message-body '{"decision":"REVIEW"}'

# Listar queues
aws --endpoint-url http://localhost:9324 sqs list-queues

# Crear secret en Moto Secrets Manager
aws --endpoint-url http://localhost:5000 secretsmanager create-secret \
  --name risk-engine/api-key --secret-string System.getenv("RISK_CLIENT_API_KEY")

# Listar secrets Moto
aws --endpoint-url http://localhost:5000 secretsmanager list-secrets

# OpenBao (Vault-compatible)
export VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=root
vault kv put secret/risk-engine/db password=poc
vault kv get secret/risk-engine/db
```

### Por que este stack y no LocalStack

LocalStack Community fue discontinuado en marzo 2026 (ver blog.localstack.cloud). Este stack usa proyectos activos y mantenidos:

- **MinIO**: Apache 2.0 (AGPL para versiones enterprise — revisar si uso comercial).
- **ElasticMQ**: Apache 2.0.
- **Moto**: Apache 2.0.
- **OpenBao**: MPL 2.0 (fork comunitario de Vault post-BSL).

Cada tool es un experto en su dominio: MinIO implementa S3 con alta fidelidad; ElasticMQ implementa SQS con soporte de DLQ; Moto usa la misma infra de tests de AWS (botocore); OpenBao es el reemplazo comunitario de Vault con API identica.

### Variante AWS-realista

El ExternalSecret del PoC apunta al backend `kubernetes` (namespace `secrets-source`). Para usar Moto como backend real de AWS Secrets Manager, cambiar el `ClusterSecretStore` a provider `aws` con `endpoint: http://moto.aws-mocks.svc:5000`. Las env vars `AWS_ENDPOINT_URL_*` ya estan configuradas en el pod template del Rollout.

---

## Limpiar

```bash
# k3d: borra el cluster completo
./scripts/down.sh

# OrbStack: borra namespaces del PoC, deja OrbStack corriendo
./scripts/down.sh

# OrbStack: borra namespaces Y deshabilita k8s
./scripts/down.sh --full
```

Los datos no persisten entre reinicios (emptyDir en aws-mocks, no hay PVCs en el PoC).

---

## Estructura del proyecto

```
poc/k8s-local/
├── scripts/
│   ├── up.sh              # Bootstrap idempotente del cluster (--provider orbstack|k3d)
│   ├── down.sh            # Destruir cluster / namespaces (--full para OrbStack)
│   ├── status.sh          # Estado de pods y ArgoCD apps
│   └── demo.sh            # Imprime port-forwards y URLs (incluye AWS mocks)
├── addons/
│   ├── 00-namespaces.yaml
│   ├── 10-argocd-values.yaml
│   ├── 20-argo-rollouts-values.yaml
│   ├── 30-kube-prom-stack-values.yaml.disabled  # DISABLED — ver vault/02-Decisions/0045-observability-stack-local.md
│   ├── 40-external-secrets-values.yaml
│   ├── 41-cluster-secret-store.yaml  # ClusterSecretStore + secret fuente local
│   ├── 50-tansu.yaml          # ADR-0043: raw Deployment + Service + topic-seed Job
│   ├── 60-openobserve-values.yaml
│   ├── 70-aws-mocks.yaml             # MinIO + ElasticMQ + Moto + OpenBao
│   └── 71-aws-mocks-init.yaml        # Job: crea buckets, queues, secrets iniciales
├── apps/
│   └── risk-engine/        # Helm chart manual
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── _helpers.tpl
│           ├── deployment.yaml    # Stub con replicas:0 (Rollout toma el control)
│           ├── service.yaml       # stable + canary services
│           ├── ingress.yaml
│           ├── rollout.yaml        # Canary: 20% → 50% → 100% con analysis
│           └── externalsecret.yaml # Lee de ClusterSecretStore local-k8s-store
├── argocd/
│   ├── project.yaml
│   ├── application-risk-engine.yaml
│   └── analysis-templates/
│       ├── success-rate.yaml  # web provider → OpenObserve SQL API: availability >= 99%
│       └── latency-p99.yaml   # web provider → OpenObserve SQL API: p99 < 300ms
└── README.md
```

---

## Talking points para review (technical leadership level)

1. **"Como testeas canaries antes de llegar a prod?"**
   Este setup demuestra el mismo ciclo que en mega: Argo Rollouts envia 20% del trafico
   al canario, corre AnalysisRuns contra la API SQL de OpenObserve durante el pause, y hace
   rollback automatico si `success-rate < 0.99` o `p99 > 300ms`. En produccion el mismo
   AnalysisTemplate apuntaria a Prometheus; el contrato de Argo Rollouts no cambia, solo
   el provider (prometheus vs. web HTTP).

2. **"Como manejas secrets en EKS sin hardcodear credenciales?"**
   External Secrets Operator con IRSA: el ServiceAccount del pod tiene una annotation
   `eks.amazonaws.com/role-arn` que mapea a un IAM Role con politica GetSecretValue
   sobre el ARN especifico en AWS Secrets Manager. ESO sincroniza y crea el Secret de
   Kubernetes; el pod solo ve variables de entorno. En este PoC, el mismo ESO corre con
   provider `kubernetes` apuntando a un namespace `secrets-source` — el contrato del
   ExternalSecret es identico, solo cambia el provider.

3. **"Que SLO definirías para un servicio de risk?"**
   Availability SLO: 99% de requests exitosos (non-5xx) medidos en ventana de 1h.
   Latency SLO: P99 < 300ms sobre ventana de 5min. En el PoC local estos SLOs alimentan
   los AnalysisTemplates de Argo Rollouts via OpenObserve. En prod los mismos SLOs se
   expresan como recording rules en Prometheus que alimentan alertas de burn-rate y
   dashboards de Grafana.

4. **"Como manejas el bootstrap de ArgoCD en un cluster nuevo?"**
   El unico paso manual es `helm install argocd` — todo lo demas es declarativo en Git.
   El `Application` apunta al path del chart con `syncPolicy.automated` + `selfHeal: true`.
   Para clusters nuevos en prod, se usa un `Application of Applications` o ApplicationSet
   que genera las Applications de todos los servicios a partir de un directorio en Git.

5. **"Por que  Tansu en lugar de MSK?"**
    Tansu es Kafka-API compatible, corre en Kubernetes nativo sin ZooKeeper, tiene
   menor latencia de tail que Apache Kafka, y el mismo operator funciona igual en local
   que en EKS. Para una PoC local elimina la dependencia de AWS; en prod la
   decision depende del costo operativo vs. managed MSK.
