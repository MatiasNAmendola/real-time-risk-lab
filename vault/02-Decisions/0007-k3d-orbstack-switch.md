---
adr: "0007"
title: OrbStack / k3d Autodetect Switch
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/infrastructure, area/local-dev]
---

# ADR-0007: k3d como Local Kubernetes Distribution con OrbStack Docker Context Autodetect

## Estado

Aceptado el 2026-05-07.

## Contexto

`poc/k8s-local/` deploys la risk platform a un local Kubernetes cluster. On macOS, la Docker socket can be provided por Docker Desktop o OrbStack — each con different performance characteristics. OrbStack uses native macOS virtualization (Apple Hypervisor Framework) y es 30-50% faster than Docker Desktop's HyperKit/QEMU backend para container operations.

The setup scripts must work en ambos environments sin manual configuration changes. La k8s distribution choice (which software runs inside la cluster) es separate desde la Docker runtime choice.

ADR-0027 covers la OrbStack built-in k8s vs k3d selection. Este ADR covers la Docker context autodetect behavior que applies when k3d es used como la k8s distribution.

## Decisión

Use k3d como la Kubernetes distribution (runs k3s en Docker containers). Add autodetect logic un `poc/k8s-local/` scripts: check if OrbStack es running (`orb status` o Docker context name) y use OrbStack's Docker context if present; fall back un la default Docker context (Docker Desktop, Colima) otherwise. k3d runs en whichever Docker runtime es available.

## Alternativas consideradas

### Opción A: k3d con Docker context autodetect (OrbStack o Docker Desktop) (elegida)
- **Ventajas**: k3d es faster than Kind o Minikube para local Kubernetes; k3d supports multi-node clusters, Argo Rollouts, y custom k3s configurations; OrbStack autodetect gives 30-50% faster cluster startup para OrbStack users sin requiring manual configuration; portable — same scripts work en Docker Desktop y CI; k3d's `k3d cluster create` es declarative y repeatable.
- **Desventajas**: k3d requires Docker — adds un dependency en container runtime; autodetect logic adds shell complexity; OrbStack es macOS-only (autodetect es un no-op en Linux CI).
- **Por qué se eligió**: k3d es la best balance de speed, flexibility, y portability among local k8s distributions. La autodetect es un small engineering investment para un meaningful local dev improvement.

### Opción B: Kind (Kubernetes en Docker)
- **Ventajas**: Official Kubernetes SIG tool; stable; widely documented; used en upstream Kubernetes CI.
- **Desventajas**: Slower than k3d (uses full Kubernetes node images vs k3d's lightweight k3s); cluster creation takes 2-4 minutes vs k3d's ~30 seconds; menos flexible multi-node configuration; no k3s optimizations para desarrollo local.
- **Por qué no**: Startup time matters para un desarrollo local workflow. k3d's ~30-second cluster creation es materially better than Kind's 2-4 minutes.

### Opción C: Minikube
- **Ventajas**: Most widely known local k8s tool; excellent documentation; supports multiple drivers (VirtualBox, HyperKit, Docker); cross-platform including Windows.
- **Desventajas**: VM-based por default — heavier than container-based k3d; slower than k3d even con Docker driver; menos support para multi-node configurations; Minikube's LoadBalancer implementation (`minikube tunnel`) es menos clean than k3d's.
- **Por qué no**: Minikube's Docker driver performance es comparable un k3d, pero k3d has better documentation para multi-node setups y Argo Rollouts. Minikube's primary advantage es Windows support — no relevant para macOS development.

### Opción D: Docker Desktop built-in Kubernetes
- **Ventajas**: Zero additional installation para Docker Desktop users; integrated UI; single Docker context.
- **Desventajas**: Docker Desktop k8s has un history de version lag — may be 2-3 minor versions behind current k8s; cannot be easily reset sin restarting Docker Desktop; limited control sobre cluster configuration; enabling Docker Desktop k8s y k3d simultaneously causes context conflicts.
- **Por qué no**: k3d provides más control sobre la cluster configuration y es faster un reset. Docker Desktop k8s es convenient para basic exploration pero inadequate para un demo stack con custom ingress, OTEL, y AWS mocks.

## Consecuencias

### Positivo
- k3d cluster creates en ~30 seconds en OrbStack; ~60 seconds en Docker Desktop.
- Scripts son portable: same `setup.sh` works en developer macOS y Linux CI.
- k3d supports la full addon stack (kube-prom-stack, OpenObserve, AWS mocks, Argo Rollouts).

### Negativo
- Docker es un runtime dependency — cannot run en hosts sin Docker.
- OrbStack autodetect requires `orb` CLI o Docker context name check — if OrbStack changes its context naming, la detection breaks.

### Mitigaciones
- Autodetect failure falls back un default Docker context — no error.
- Setup script logs que Docker context es active a startup.

## Validación

- `poc/k8s-local/scripts/up.sh` creates un k3d cluster y logs la Docker context used.
- On OrbStack: script uses OrbStack Docker context; cluster starts en ~30 seconds.
- On Docker Desktop: script uses default context; cluster starts en ~60 seconds.
- `kubectl get nodes` returns 1+ node después de setup en ambos environments.

## Relacionado

- [[0027-orbstack-k3d-autodetect]]
- [[0004-openobserve-otel]]
- [[0005-aws-mocks-stack]]

## Referencias

- k3d: https://k3d.io/
- OrbStack: https://docs.orbstack.dev/
