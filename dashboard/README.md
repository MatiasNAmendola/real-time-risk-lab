# Dashboard — Real-Time Risk Lab

Static HTML dashboard centralizado con accesos a todos los servicios, APIs y reportes.

## Modo Docker compose

```bash
cd dashboard
./scripts/start.sh
# Abre: http://localhost:8888
```

Asume que los servicios (Vertx app, OpenObserve, Redpanda, AWS mocks) están corriendo en `poc/vertx-layer-as-pod-eventbus/` con `./scripts/up.sh`.

## Modo k8s

```bash
kubectl apply -f k8s/homer.yaml
echo "127.0.0.1 dashboard.local" | sudo tee -a /etc/hosts
# O alternativa sin /etc/hosts:
kubectl -n homer port-forward svc/homer 8888:80
```

## Edicion

El config vive en `assets/config.yml`. Reload: `docker compose restart homer` (o `kubectl rollout restart -n homer deployment/homer`).

## Estructura del dashboard

- **Apps**: Risk Engine Vertx + bare-javac, healthchecks.
- **API Documentation**: OpenAPI 3.1 + AsyncAPI 3.0 + Swagger UI + AsyncAPI Studio.
- **Observability**: OpenObserve, Grafana, Prometheus, Alertmanager.
- **Streaming**: Redpanda Console + Admin API.
- **AWS Mocks**: MinIO, ElasticMQ, Moto, OpenBao.
- **Data**: Postgres, Valkey.
- **ArgoCD (k8s mode)**: GitOps + Argo Rollouts.
- **Tests & Reports**: smoke, ATDD, perf comparison.

## Conflicto de puertos

MinIO Console (9001) y Redpanda Console usan puertos distintos (9001 vs 9000) en esta configuracion — no hay colision directa. Sin embargo, si el docker-compose principal mapea Redpanda Console a 9001 en lugar de 9000, la tile de MinIO quedaria apuntando al servicio incorrecto. Verificar con `docker compose ps` en `poc/vertx-layer-as-pod-eventbus/` y ajustar la URL en `assets/config.yml` si corresponde.

## Archivos

```
dashboard/
├── docker-compose.yml        # Homer standalone, port 8888
├── assets/
│   ├── config.yml            # Config Homer con todos los tiles
│   ├── icons/                # Iconos custom (opcional)
│   └── logo.svg              # Logo NX placeholder
├── k8s/
│   └── homer.yaml            # Namespace + ConfigMap + Deployment + Service + Ingress
├── scripts/
│   └── start.sh              # docker compose up -d wrapper
└── README.md
```
