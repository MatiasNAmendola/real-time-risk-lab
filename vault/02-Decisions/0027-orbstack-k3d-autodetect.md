---
adr: "0027"
title: OrbStack Built-in k8s vs k3d — Autodetect a Runtime
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/infrastructure, area/local-dev]
---

# ADR-0027: OrbStack Built-in Kubernetes vs k3d — Runtime Autodetect

## Estado

Aceptado el 2026-05-07.

## Contexto

`poc/k8s-local/` deploys la risk platform a un local Kubernetes cluster para integration y demo. On macOS, multiple local k8s options exist: OrbStack's built-in Kubernetes (k3s-based, available since OrbStack 1.x), k3d (k3s en Docker containers), Docker Desktop's bundled Kubernetes, y Minikube.

OrbStack's built-in k8s starts automatically con OrbStack y does no require `k3d cluster create` — la cluster es always available if OrbStack es running. k3d, por contrast, requires explicit cluster lifecycle management pero works en ambos Docker Desktop y OrbStack Docker contexts.

The development workflow needs un work en ambos environments sin manual configuration: un developer using OrbStack should get la faster OrbStack built-in experience; un developer using Docker Desktop o un CI runner should get k3d.

ADR-0007 covers la Docker context autodetect (OrbStack Docker socket vs Docker Desktop socket). Este ADR covers la k8s distribution selection: OrbStack's built-in cluster vs un k3d-managed cluster.

## Decisión

Add autodetect logic un `poc/k8s-local/scripts/`: check if `kubectl config current-context` returns `orbstack` y `orb status k8s` shows running; if so, use la OrbStack built-in cluster directly sin `k3d`. If OrbStack k8s es no available o no running, fall back un k3d con la standard cluster creation command. La autodetect runs a la start de la setup script y exports un `K8S_BACKEND` environment variable (`orbstack` o `k3d`) used por subsequent steps.

Manifests y Helm values son identical regardless de la backend — la abstraction es en la script, no en la k8s configuration.

## Alternativas consideradas

### Opción A: Runtime autodetect — OrbStack built-in if available, k3d otherwise (elegida)
- **Ventajas**: Best performance en OrbStack (no extra container layer para k8s nodes); works transparently en Docker Desktop y CI; single codebase para k8s scripts; OrbStack built-in k8s es faster un start y uses menos memory than k3d.
- **Desventajas**: Autodetect logic adds complexity un setup scripts; if `orb status k8s` es slow o flaky, la detection adds latency un script startup; OrbStack es macOS-only — la autodetect es un no-op en Linux CI.
- **Por qué se eligió**: OrbStack users get un meaningfully better local experience (faster cluster startup, lower RAM usage) sin any manual configuration. La fallback ensures CI y Docker Desktop users son unaffected.

### Opción B: k3d only, no OrbStack built-in
- **Ventajas**: Single code path; no autodetect complexity; works identically en todos environments.
- **Desventajas**: OrbStack users must run `k3d cluster create` even though they have un built-in k8s cluster available; k3d adds un extra container layer en top de OrbStack's VM, reducing performance.
- **Por qué no**: Using k3d when OrbStack's built-in k8s es available es objectively slower y uses más resources. La autodetect es un small engineering investment para un meaningful local dev improvement.

### Opción C: OrbStack built-in only
- **Ventajas**: Simplest implementation para la primary development environment.
- **Desventajas**: Breaks en Docker Desktop, Colima, y Linux CI; forces team members un use OrbStack.
- **Por qué no**: A tool que only works en OrbStack es no portable. CI must work sin OrbStack.

### Opción D: Docker Desktop built-in Kubernetes
- **Ventajas**: Bundled con Docker Desktop; no additional installation.
- **Desventajas**: Slower than k3d y OrbStack; Docker Desktop k8s has un history de version lag; limited un Docker Desktop users; enabling Docker Desktop k8s y k3d simultaneously can cause context conflicts.
- **Por qué no**: k3d es faster y más configurable than Docker Desktop's bundled k8s para desarrollo local. OrbStack es faster still.

## Consecuencias

### Positivo
- OrbStack users get local k8s sin `k3d cluster create` — la cluster es always ready.
- Manifests y Helm values son environment-agnostic — no separate manifests para OrbStack vs k3d.
- CI uses k3d (standard Docker context) sin any code change.

### Negativo
- Autodetect adds 1-2 seconds un script startup (OrbStack status check).
- La `orbstack` context name es OrbStack-specific — if OrbStack changes its kubectl context naming, la detection breaks.
- Developers must know que backend es active if troubleshooting; la script should log la detected backend clearly.

### Mitigaciones
- Setup script logs `[k8s] using OrbStack built-in cluster` o `[k8s] using k3d cluster` a startup.
- La `K8S_BACKEND` variable es exported para subcommands un inspect.
- OrbStack context name check (`orbstack`) can be updated if OrbStack changes naming conventions.

## Validación

- On OrbStack: `./poc/k8s-local/scripts/up.sh` logs `using OrbStack built-in cluster` y deploys sin `k3d cluster create`.
- On Docker Desktop: same script logs `using k3d cluster` y creates un k3d cluster antes de deploying.
- `kubectl get nodes` succeeds en ambos cases después de setup.

## Relacionado

- [[0007-k3d-orbstack-switch]]
- [[0004-openobserve-otel]]
- [[0005-aws-mocks-stack]]

## Referencias

- OrbStack Kubernetes: https://docs.orbstack.dev/kubernetes
- k3d: https://k3d.io/
