---
title: Canary Deployment
tags: [concept, deployment, kubernetes]
created: 2026-05-07
---

# Canary Deployment

Estrategia de rollout progresivo: la nueva versión recibe un porcentaje chico del tráfico (p. ej., 10%), monitoreada contra SLIs; si las métricas pasan, el tráfico crece; si fallan, rollback. Argo Rollouts lo automatiza en Kubernetes.

## Cuándo usar

Todo cambio crítico donde un mal deploy podría agotar el error budget. Cambios al motor de riesgo que afectan el scoring transaccional en vivo son candidatos directos.

## Cuándo NO usar

Herramientas internas, jobs batch, o servicios con impacto despreciable para el usuario.

## En este proyecto

[[k8s-local]] implementa un Rollout con steps canary: 10% → wait 5m → analyze → 50% → wait 5m → analyze → promote. Los AnalysisTemplates chequean los gates de [[SLI-SLO-Error-Budget]]. Ver [[0007-k3d-orbstack-switch]] para el setup local.

## Principio de diseño

"Un canary es una hipótesis: 'este cambio es seguro'. El AnalysisTemplate es cómo lo falsificamos antes de que llegue al 100% del tráfico."

## Backlinks

[[SLI-SLO-Error-Budget]] · [[k8s-local]] · [[Observability]]
