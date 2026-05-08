---
title: k8s-local PoC
tags: [poc, kubernetes, k3d, canary]
created: 2026-05-07
source: poc/k8s-local/
---

# k8s-local

Entorno Kubernetes local que demuestra GitOps + canary deployment + observabilidad + gestión de secrets + servicios mock de AWS, todo grado producción. Corre sobre k3d (o OrbStack, ver [[0007-k3d-orbstack-switch]]).

## Qué demuestra

- [[Canary-Deployment]] vía Argo Rollouts con AnalysisTemplates de Prometheus
- GitOps con ArgoCD
- [[External-Secrets-Operator]] con provider kubernetes
- Simulación de identidad de workload estilo [[IRSA]]
- Servicios mock de AWS in-cluster (ver [[0005-aws-mocks-stack]])
- [[SLI-SLO-Error-Budget]] — gates de success-rate y latency-p99 en el canary

## Stack

| Componente | Versión |
|------------|---------|
| k3d | latest |
| ArgoCD | 2.x |
| Argo Rollouts | 1.x |
| kube-prom-stack | Prometheus + Grafana |
| ESO | External Secrets Operator |
| Redpanda | latest |
| OpenObserve | latest |

## Cómo correrlo

```bash
cd poc/k8s-local
poc/k8s-local/scripts/up.sh          # crea cluster k3d + instala addons
poc/k8s-local/scripts/up.sh  # despliega el Helm chart
kubectl argo rollouts get rollout risk-engine --watch
```

## Archivos clave

```
poc/k8s-local/
  helm/risk-engine/         Helm chart con Rollout (canary)
  manifests/
    10-argocd.yaml
    20-argo-rollouts.yaml
    30-kube-prom-stack.yaml
    40-eso.yaml
    50-redpanda.yaml
    60-openobserve.yaml
    70-aws-mocks.yaml         Moto + MinIO + ElasticMQ + OpenBao + DynamoDB Local
    71-aws-mocks-init.yaml    init job: seed de datos de test
  scripts/
    bootstrap.sh              autodetecta OrbStack vs k3d
    deploy-risk-engine.sh
```

## Lógica del gate de canary

El AnalysisTemplate chequea:
1. `success_rate > 0.99` en una ventana de 5m
2. `latency_p99 < 300ms` en una ventana de 5m

Si alguno falla → rollback. Si ambos pasan en 2 intervalos consecutivos → promote.

## Conceptos aplicados

[[Canary-Deployment]] · [[SLI-SLO-Error-Budget]] · [[IRSA]] · [[External-Secrets-Operator]] · [[Observability]]

## Decisiones

[[0004-openobserve-otel]] · [[0005-aws-mocks-stack]] · [[0007-k3d-orbstack-switch]]

## Talking points de diseño

- "El gate de canary es Prometheus-native. El mismo AnalysisTemplate funciona en EKS productivo con el kube-prom-stack real — cero esfuerzo de migración."
- ESO con provider kubernetes simula IRSA sin requerir credenciales AWS reales en local.
