---
title: K8s Deployment Tests — suite de tests de mecanismos de despliegue
tags: [poc, kubernetes, testing, canary, blue-green, argocd, gitops]
created: 2026-05-12
source_archive: docs/36-k8s-deployment-tests.md (migrado 2026-05-12)
---

# K8s Deployment Tests — suite de tests de mecanismos de despliegue

Este documento explica qué cubre la suite `tests/k8s-deployment`, cómo se extiende y cómo encaja en la arquitectura de testing del repo.

## Estrategias cubiertas

| Estrategia      | Test                  | Mecanismo crítico verificado                       |
|-----------------|-----------------------|---------------------------------------------------|
| Canary          | `CanaryRolloutTest`   | `setWeight 20→50→100` con AnalysisTemplate ok     |
| Canary rollback | `CanaryRollbackTest`  | AnalysisRun falla → status `Degraded`, image v1   |
| Blue/Green      | `BlueGreenTest`       | `activeService` swap entre ReplicaSets            |
| RollingUpdate   | `RollingUpdateTest`   | Cero downtime durante el rollout                  |
| GitOps          | `ArgoCDSyncTest`      | Application llega a `Synced + Healthy`            |
| Secrets         | `ExternalSecretsTest` | ESO reconcilia ExternalSecret → Secret nativo     |
| Smoke           | `SmokeTest`           | `helm install` + pods Ready + `/healthz` 2xx      |

## Cómo correr

```bash
./nx up k8s
./nx test k8s-smoke         # ~1 min
./nx test k8s-canary        # ~6 min (rollout + rollback)
./nx test --composite ci-k8s
./nx down k8s --cleanup-k8s
```

Para depurar un único test:

```bash
./gradlew :tests:k8s-deployment:test -Pk8s --tests '*BlueGreenTest' --info
```

## Convenciones del módulo

- Cada test crea un namespace ephemeral (`k8stest-<random>`); ningún test comparte estado.
- Cluster reachability se chequea en `ClusterPreflight` (skip silencioso si no hay cluster) — los tests no fallan por "no hay k8s".
- `KubectlClient` parsea JSON con Jackson para todos los asserts; nunca se hace `grep` sobre stdout.
- Timeouts: 2 min pod Ready, 1 min step canary, 5 min canary completo.

## Cómo agregar un test nuevo

1. Crear `XYZTest.java` en `src/test/java/io/riskplatform/k8stest/`.
2. Anotar la clase con `@ExtendWith(ClusterPreflight.class)`.
3. `@BeforeEach` crear ns + KubectlClient + HelmClient.
4. `@AfterEach` `helm uninstall` y `kubectl delete namespace`.
5. Si necesitás un manifest específico, ponerlo en `src/test/resources/manifests/`.

## Output esperado (smoke)

```
> Task :tests:k8s-deployment:test

SmokeTest > chartInstallsAndPodsBecomeReady() PASSED
SmokeTest > healthzEndpointAnswersInsideCluster() PASSED

BUILD SUCCESSFUL in 1m 12s
```

## Estado actual

La suite fue diseñada y compila, pero la ejecución verde requiere `k3d` (o OrbStack k8s habilitado) en el host.

```bash
brew install k3d
./nx up k8s
./nx test k8s-smoke   # primera ejecución verde
```

## Related

- [[0041-k8s-deployment-test-strategy]] — ADR de estrategia de tests de despliegue.
- [[Canary-Deployment]] — concepto de canary deployment.
- [[k8s-local]] — PoC de k8s local con ArgoCD.
- [[External-Secrets-Operator]] — gestión de secrets en k8s.
- [[Failure-Debug-Toolkit]] — herramientas de diagnóstico cuando los tests fallan.
- [[Risk-Platform-Overview]]
