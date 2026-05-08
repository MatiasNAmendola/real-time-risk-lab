# Security boundaries locales

Esta PoC separa procesos para simular pods con permisos distintos.

## controller-pod

- Expone API pública `POST /risk/evaluate`.
- Tiene token `${CONTROLLER_TO_USECASE_TOKEN:-change-me-controller-usecase-token}`.
- Scope: `risk:evaluate`.
- No tiene permiso para hablar con repository.

## usecase-pod

- Expone API interna `POST /internal/risk/evaluate`.
- Acepta solo token del controller.
- Tiene token para repository.
- Scope hacia repository: `repository:rw`.
- Orquesta reglas, ML simulado, idempotencia, persistencia y outbox.

## repository-pod

- Expone solo endpoints `/internal/*`.
- Acepta solo token `${USECASE_TO_REPOSITORY_TOKEN:-change-me-usecase-repository-token}` + scope `repository:rw`.
- Es dueño de decisiones, idempotencia y outbox.

## Traducción a Kubernetes/EKS

En EKS esto se puede mapear a:

- `ServiceAccount` distinto por deployment.
- IAM Roles for Service Accounts si toca AWS.
- NetworkPolicy: controller -> usecase, usecase -> repository, controller -x-> repository.
- Secrets separados por pod.
- Resource limits diferentes según perfil.
- HPA independiente por capa.

Frase para review:

> Separar pods no es solo escalar distinto; también sirve para reducir blast radius y aplicar least privilege por boundary arquitectónico.
