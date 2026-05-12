# tests/k8s-deployment

Integration test suite that validates the Kubernetes deployment mechanisms used
by `poc/k8s-local/`. Tests exercise a **real** local cluster (k3d or OrbStack) —
there is no mocking of `kubectl`. Each test is idempotent: setup creates a fresh
namespace, teardown removes it.

## What is covered

| Test class               | Strategy / mechanism                                    |
|--------------------------|---------------------------------------------------------|
| `SmokeTest`              | Cluster reachable, helm install OK, pods Ready, `/healthz` 200 |
| `CanaryRolloutTest`      | Argo Rollouts canary: 20% → analysis → 50% → 100%       |
| `CanaryRollbackTest`     | Failing AnalysisTemplate triggers automatic rollback    |
| `BlueGreenTest`          | Alternative `blueGreen` strategy with `activeService` swap |
| `RollingUpdateTest`      | Default `RollingUpdate` zero-downtime guarantee         |
| `ArgoCDSyncTest`         | ArgoCD `Application` reaches `Synced` + `Healthy`       |
| `ExternalSecretsTest`    | ESO reconciles `ExternalSecret` from Moto Secrets Manager |

## Prerequisites

- `kubectl` ≥ 1.28
- `helm` ≥ 3.13
- `k3d` ≥ 5.6 **or** OrbStack (with Kubernetes enabled)
- Argo Rollouts controller installed (handled by `./nx up k8s`)
- ArgoCD installed (handled by `./nx up k8s`)
- External Secrets Operator + Moto (handled by `./nx up k8s`)

## How to run

```bash
# Bring up the cluster + addons
./nx up k8s

# Run smoke only (~1 min)
./nx test k8s-smoke

# Run canary tests (~6 min, two scenarios)
./nx test k8s-canary

# Run everything in this module
./nx test k8s-rollouts

# Or directly with gradle
./gradlew :tests:k8s-deployment:test -Pk8s --tests '*SmokeTest'

# Tear down
./nx down k8s
```

## How to add a new test

1. Add a `@Test` method to an existing class, or create a new `*Test.java` under
   `src/test/java/io/riskplatform/k8stest/`.
2. Use `KubectlClient` for all cluster interactions (do not shell out raw).
3. Always use a unique namespace via `Namespaces.ephemeral()` — never reuse
   `default` or `risk`.
4. Wrap waits in `Conditions.waitFor(predicate, timeout)` with a realistic
   timeout (see "Timeouts" below).
5. Register a `@AfterEach` cleanup that deletes the namespace.

## Timeouts (project convention)

| Operation                | Timeout  |
|--------------------------|----------|
| Pod Ready                | 2 min    |
| Single rollout step      | 1 min    |
| Full canary (5 steps)    | 5 min    |
| ArgoCD app Synced+Healthy| 3 min    |
| ExternalSecret reconcile | 1 min    |

## Status

**Designed but not executed in CI.** This host (`uname -m` of the agent that
generated this suite) does not have `k3d` installed, so the suite was scaffolded
and verified to compile but the actual `./gradlew :tests:k8s-deployment:test
-Pk8s` execution is gated on installing `k3d` (or enabling OrbStack k8s).

See `vault/03-PoCs/K8s-Deployment-Tests.md` for output samples and CI integration
notes.
