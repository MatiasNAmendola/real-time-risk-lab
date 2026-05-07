---
title: External Secrets Operator
tags: [concept, kubernetes, secrets, security]
created: 2026-05-07
---

# External Secrets Operator

Operator de Kubernetes que sincroniza secrets desde stores externos (AWS Secrets Manager, Vault/OpenBao, GCP Secret Manager) hacia Secrets de Kubernetes. Declarativo: el CRD `ExternalSecret` especifica origen y destino.

## Cuándo usar

Todo deployment productivo de Kubernetes. Mantiene los secrets fuera de Git y habilita rotación sin reinicio de pods (vía refresh interval).

## Cuándo NO usar

Dev local con servicios mock — usar ConfigMaps o variables de entorno directamente.

## En este proyecto

[[k8s-local]] instala ESO con el provider `kubernetes` (lee Secrets de k8s en un namespace origen). En EKS productivo, el provider sería `aws` con binding [[IRSA]].

## Principio de diseño

"ESO desacopla el ciclo de vida del secret del ciclo de vida del deployment. La rotación ocurre en el secret store; el pod lo ve en el próximo intervalo de sync."

## Backlinks

[[IRSA]] · [[k8s-local]] · [[0005-aws-mocks-stack]]
