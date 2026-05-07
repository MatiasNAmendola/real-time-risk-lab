---
title: IRSA — IAM Roles for Service Accounts
tags: [concept, aws, security, kubernetes]
created: 2026-05-07
---

# IRSA

IAM Roles for Service Accounts. Permite que pods de Kubernetes en EKS asuman roles de IAM de AWS sin credenciales estáticas. La ServiceAccount del pod se anota con un ARN de rol; AWS STS emite tokens de corta vida vía federación OIDC.

## Cuándo usar

Todo workload EKS que necesita acceso a la API de AWS (S3, SQS, Secrets Manager). Reemplaza instance profiles de EC2 y elimina access keys de larga duración.

## Cuándo NO usar

Desarrollo local donde los servicios mock (ver [[0005-aws-mocks-stack]]) no necesitan IAM real.

## En este proyecto

[[k8s-local]] simula IRSA con [[External-Secrets-Operator]] usando el provider kubernetes (lee desde Secrets de k8s en lugar de AWS real). En EKS productivo, ESO usaría el provider IRSA en cambio.

## Principio de diseño

"IRSA significa cero secrets en el codebase. La identidad del pod es su credencial AWS — y expira automáticamente."

## Backlinks

[[External-Secrets-Operator]] · [[k8s-local]] · [[0005-aws-mocks-stack]]
