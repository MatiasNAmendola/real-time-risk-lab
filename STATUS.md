# Estado del repositorio — share-ready snapshot

Fecha: **2026-05-07**  
Scope: verificación local para demo técnica de Real-Time Risk Lab.

## Resumen ejecutivo

El repo queda compartible como **exploración técnica curada**, no como sistema productivo final. Los checks que antes podían ensuciar la demo fueron corregidos o re-clasificados explícitamente como deep dive opcional.

## Matriz verificada

| Área | Comando / evidencia | Estado |
|---|---|---|
| Git | `git init` + `.gitignore` ampliado | OK |
| Tooling | `./nx setup --verify` | OK; herramientas deep-dive opcionales: k3d, kustomize, mc, otel-cli, websocat |
| Check rápido live | `./nx test --composite quick` | OK: 1/1 job PASS, 2.9s; no invoca Gradle/JUnit |
| Unit + arquitectura real | `./nx test --composite ci-fast` | Composite recomendado para pre-push/CI rápido; incluye `unit-java-fast` + `arch` + SDK unit |
| Consistencia docs | `./nx audit consistency` | OK: 91.9%, strict threshold 80% |
| Términos prohibidos públicos | Incluido en consistency audit | OK: 0 matches |
| Confidentiality | `./nx audit confidentiality` | OK: scan real con blocklist no vacía |
| Scrub secretos/PII | `./nx scrub` | OK: sin patrones obvios |
| Build live recomendado | `./nx build` | OK; build incremental/orquestado por el repo |
| Build full/CI opcional | `./nx build --legacy-clean -x test` / `./gradlew clean build -x test` | OK en corrida full; no usar como comando principal live |
| Risk engine HTTP | `./nx run risk-engine` + POST `/risk` | OK en corrida previa |
| Vert.x local pods | `run-local-pods.sh` + `smoke.sh` | OK: wait de health determinístico + evaluate + idempotencia + 403 por scope, stop limpio |
| Compose Vert.x distribuido | `./nx up vertx` | Healthcheck controller alineado a `/health`; usar como demo avanzada con warm-up |
| Bench in-process | `./nx bench inproc` | OK; resultados en `bench/build/bench-inprocess/results.json`; p99 sample del core 0.001 ms/op, p99.9 0.121 ms/op; parte del demo principal |

## Fixes aplicados en la pasada share-ready

1. Inicialización de repo Git local y `.gitignore` robusto.
2. `./nx setup --verify`: k3d/kustomize/mc/otel-cli/websocat no bloquean core demo; se muestran como opcionales.
3. `./nx test --composite quick`: redefinido como guardrail live sub-segundo/segundos (`quick-check`) para evitar esperas imposibles durante demo.
4. `arch` queda `exclusive` dentro de `ci-fast` para evitar carrera XML cuando se corren unit + ArchUnit reales.
5. Consistency audit: `vault/05-Methodology/Architecture-Question-Bank.md` agregado como fuente Q&A; `./nx audit consistency` corre en modo strict.
6. Confidentiality: `.ai/blocklist.sha256` no vacía para evitar falso “scan skipped”.
7. Scrub: mock password de OpenBao convertido a variable local.
8. Términos públicos residuales limpiados: audit queda en 0 matches.
9. Compose distribuido: healthcheck de `controller-app` corregido de `/healthz` a `/health`, retries/start period ampliados.
10. Namespace público limpiado: packages/domains/SDK placeholders pasan de referencias específicas a `io.riskplatform` / `riskplatform`.

## Cómo demostrarlo sin riesgo

Frase recomendada para compartirlo:

> Te comparto una exploración técnica curada para discutir arquitectura de decisiones de riesgo en tiempo real. No intenta ser producción cerrada, sino una demo conversacional para hablar de trade-offs: Clean Architecture, boundaries, performance, trazabilidad, evaluación sincrónica, eventos asíncronos, permisos entre componentes, benchmarks y simulación local de despliegue distribuido.

Ver [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md). Recomendación:

1. Mostrar primero `README.md`, `vault/05-Methodology/Architecture-Question-Bank.md` y ADRs.
2. Correr `./nx setup --verify`, `./nx build`, `./nx test --composite quick`, `./nx audit consistency`.
3. Mostrar `poc/vertx-layer-as-pod-http` local pods porque demuestra separación por capas/permisos de manera determinística.
4. Correr `./nx bench inproc` para respaldar performance con medición reproducible.
5. Dejar `./nx up vertx` como deep dive si hay tiempo, no como primer comando de la demo.

## Pendientes honestos

- El baseline de compilación real está en Java 21 LTS; Java 25 está documentado como objetivo/ADR, no como runtime principal actual.
- El compose distribuido completo es más pesado y puede requerir warm-up; para demo live conviene levantarlo antes o usar el PoC local pods.
- La blocklist de confidentiality está activa y no vacía. Para una publicación pública real, cargar también hashes privados específicos de nombres/proyectos/personas que no deban aparecer.
