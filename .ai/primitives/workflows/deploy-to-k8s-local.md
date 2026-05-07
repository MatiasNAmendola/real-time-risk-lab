---
name: deploy-to-k8s-local
description: Levantar el stack completo en k3d u OrbStack con ArgoCD, addons y la aplicacion
steps: [select-provider, up, addons, app, verify]
---

# Workflow: deploy-to-k8s-local

## Prerequisitos

- Docker Desktop o OrbStack instalado y corriendo.
- `kubectl`, `helm`, `argocd` CLI en PATH.
- `k3d` si no se usa OrbStack.

## 1. Seleccionar provider

```bash
# OrbStack (default si esta disponible):
K8S_PROVIDER=orbstack ./poc/k8s-local/scripts/up.sh

# k3d (cross-platform):
K8S_PROVIDER=k3d ./poc/k8s-local/scripts/up.sh

# Autodetect (default):
./poc/k8s-local/scripts/up.sh
```

## 2. El script up.sh hace:

1. Crea cluster (k3d: `k3d cluster create risk-local`) o configura OrbStack k8s.
2. Instala addons via Helm en orden:
   - `00-namespaces.yaml` — namespaces
   - ArgoCD (chart 9.2.4)
   - Argo Rollouts (2.40.5)
   - kube-prometheus-stack (80.11.0)
   - External Secrets (1.2.1)
   - Redpanda
   - OpenObserve
   - AWS mocks (Moto, MinIO, ElasticMQ, OpenBao)
3. Aplica `argocd/application-risk-engine.yaml` para el app.

## 3. Verificar addons

```bash
kubectl get pods -A
# Todos deben estar Running/Completed

# ArgoCD UI:
kubectl port-forward svc/argocd-server 8090:443 -n argocd
# https://localhost:8090 (admin / password de argocd-initial-admin-secret)

# Prometheus:
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090 -n monitoring

# OpenObserve:
kubectl port-forward svc/openobserve 5080 -n monitoring
# http://localhost:5080
```

## 4. Deploy de la app via ArgoCD

```bash
# Forzar sync si no se sincroniza automatico:
argocd app sync risk-engine

# Verificar:
kubectl get rollout risk-engine -n risk-engine
kubectl argo rollouts status risk-engine -n risk-engine
```

## 5. Smoke test

```bash
# Desde cli/risk-smoke:
cd cli/risk-smoke && go run . --base-url http://risk-engine.local
# o si hay port-forward:
kubectl port-forward svc/risk-engine 8080:8080 -n risk-engine
cd cli/risk-smoke && go run .
```

## 6. Teardown

```bash
./poc/k8s-local/scripts/down.sh         # borra namespaces del PoC
./poc/k8s-local/scripts/down.sh --full  # borra el cluster entero (OrbStack: deshabilita k8s)
```

## Troubleshooting

| Problema | Solucion |
|---|---|
| Pod en CrashLoopBackOff | `kubectl logs <pod> -n <ns> --previous` |
| ArgoCD OutOfSync | `argocd app sync risk-engine --force` |
| Prometheus no scrape | Verificar label `release: kube-prometheus-stack` en ServiceMonitor |
| ESO secret no creado | `kubectl describe externalsecret -n risk-engine` |
