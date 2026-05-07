# Estado del repositorio — share-ready snapshot

Fecha: **2026-05-07**  
Scope: verificación local para demo técnica de Risk Decision Platform.

## Resumen ejecutivo

El repo queda compartible como **exploración técnica curada**, no como sistema productivo final. Los checks que antes podían ensuciar la demo fueron corregidos o re-clasificados explícitamente como deep dive opcional.

## Matriz verificada

| Área | Comando / evidencia | Estado |
|---|---|---|
| Git | `git init` + `.gitignore` ampliado | OK |
| Tooling | `./nx setup --verify` | OK; herramientas deep-dive opcionales: k3d, kustomize, mc, otel-cli, websocat |
| Tests rápidos | `./nx test --composite quick` | OK: 2/2 jobs PASS, 21.6s |
| Arquitectura | `arch` corre después de `unit` para evitar carrera de XML | OK |
| Consistencia docs | `./nx audit consistency` | OK: 91.9%, strict threshold 80% |
| Términos prohibidos públicos | Incluido en consistency audit | OK: 0 matches |
| Confidentiality | `./nx audit confidentiality` | OK: scan real con blocklist no vacía |
| Scrub secretos/PII | `./nx scrub` | OK: sin patrones obvios |
| Build general | `./gradlew clean build -x test` | OK: BUILD SUCCESSFUL, 31s, 130 tasks |
| Risk engine HTTP | `./nx run risk-engine` + POST `/risk` | OK en corrida previa |
| Vert.x local pods | `run-local-pods.sh` + `smoke.sh` | OK: health + evaluate + idempotencia + 403 por scope, stop limpio |
| Compose Vert.x distribuido | `./nx up vertx` | Healthcheck controller alineado a `/health`; usar como demo avanzada con warm-up |
| Bench in-process | `./nx bench inproc` | OK en corrida previa; resultados en `bench/build/bench-inprocess/results.json` |

## Fixes aplicados en la pasada share-ready

1. Inicialización de repo Git local y `.gitignore` robusto.
2. `./nx setup --verify`: k3d/kustomize/mc/otel-cli/websocat no bloquean core demo; se muestran como opcionales.
3. `./nx test --composite quick`: `arch` marcado `exclusive` para evitar carrera con `unit`.
4. Consistency audit: `docs/09-architecture-question-bank.md` agregado como fuente Q&A; `./nx audit consistency` corre en modo strict.
5. Confidentiality: `.ai/blocklist.sha256` no vacía para evitar falso “scan skipped”.
6. Scrub: mock password de OpenBao convertido a variable local.
7. Términos públicos residuales limpiados: audit queda en 0 matches.
8. Compose distribuido: healthcheck de `controller-app` corregido de `/healthz` a `/health`, retries/start period ampliados.

## Cómo demostrarlo sin riesgo

Ver [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md). Recomendación:

1. Mostrar primero `README.md`, `docs/09-architecture-question-bank.md` y ADRs.
2. Correr `./nx setup --verify`, `./nx test --composite quick`, `./nx audit consistency`.
3. Mostrar `poc/vertx-risk-platform` local pods porque demuestra separación por capas/permisos de manera determinística.
4. Dejar `./nx up vertx` como deep dive si hay tiempo, no como primer comando de la demo.

## Pendientes honestos

- El baseline de compilación real está en Java 21 LTS; Java 25 está documentado como objetivo/ADR, no como runtime principal actual.
- El compose distribuido completo es más pesado y puede requerir warm-up; para demo live conviene levantarlo antes o usar el PoC local pods.
- La blocklist de confidentiality contiene placeholders; para una publicación pública real habría que cargar hashes privados de nombres/proyectos/personas específicos.
