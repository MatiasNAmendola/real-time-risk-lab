---
adr: "0041"
title: Estrategia de tests para los mecanismos de despliegue en k8s
status: accepted
date: 2026-05-07
tags: [decision/accepted, k8s, testing, rollouts]
---

# ADR-0041: Estrategia de tests para los mecanismos de despliegue en k8s

## Contexto

El stack `poc/k8s-local/` (k3d + ArgoCD + Argo Rollouts + ESO) ya existe y
modela canary, blue/green, RollingUpdate, GitOps y secret reconciliation, pero
ningún test automatizado verifica que esos mecanismos sigan funcionando ante
cambios al chart, AnalysisTemplates o manifests. La regresión silenciosa más
común es que un cambio al chart (e.g., probe path, weight steps) rompa la
promoción canary sin que nadie lo note hasta el siguiente despliegue real.

## Decisión

Crear el módulo `tests/k8s-deployment` (JUnit 5 + AssertJ + Jackson, Java 21)
que ejecuta contra un cluster real local y valida cada estrategia de despliegue
end-to-end. Argo Rollouts es el motor canónico (NO Flagger). Los AnalysisRuns
son los que dictan promote/rollback (no condiciones manuales del test).

## Consecuencias

- Ventajas:
  - Detección temprana de regresiones en chart, AnalysisTemplates y
    Application manifests.
  - Cobertura de las 3 estrategias (canary, blueGreen, RollingUpdate) en
    una sola suite con timeouts realistas.
  - Mismo runner que el resto del repo (`./nx test`, `test-runner.py`).
- Desventajas:
  - Requiere cluster local (k3d/OrbStack); no corre en CI hosted estándar
    sin un runner self-hosted o un GitHub Actions con `kind`.
  - Tiempos largos (canary completo ~5 min) → no apto para "fast feedback".
- Mitigaciones:
  - `values-test.yaml` recorta los pause de 30s → 5s para mantener la
    suite total < 15 min.
  - El módulo está gated por `-Pk8s` así un `./gradlew test` accidental no
    intenta levantar un cluster.
  - `ClusterPreflight` JUnit ExecutionCondition skipea si `kubectl
    cluster-info` falla, así no se bloquean PRs en hosts sin cluster.

## Alternativas consideradas

- **Flagger**: análogo a Argo Rollouts pero acoplado a service-mesh (Istio/
  Linkerd). Risk Platform ya descartó service mesh en este POC (ver ADR-0007).
- **Custom test harness en Go con client-go**: más rápido pero duplica
  abstracciones que ya tenemos en Java; el equipo de plataforma trabaja
  Java/Kotlin.
- **Sólo tests "diff" estáticos contra los manifests**: no detectaría problemas
  de runtime (e.g., AnalysisRun mal scoped al namespace).
- **Tests directos contra mocks de la API de Argo Rollouts**: pierde
  fidelidad — el bug típico está en cómo el controller real reacciona, no en
  la firma de los CRDs.

## Relacionado

- [[0007-k3d-orbstack-switch]]
- [[0036-archunit-structural-verification]]
- [[poc/k8s-local/README]]
- `tests/k8s-deployment/README.md`
- `vault/03-PoCs/K8s-Deployment-Tests.md`
