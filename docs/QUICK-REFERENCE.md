# Quick Reference — CLI `nx`

Audiencia: operadores que llegan al repo. Tabla completa de subcomandos del orquestador `./nx`. Para texto completo de uso correr `./nx help [<comando>]`.

> Estado actual del CLI tras Phase 6 + Phase 9. Solo se documentan flags y subcomandos verificados con `./nx <cmd> --help`.

Documentos relacionados:
[doc 26](26-java-version-compat-2026.md) · [doc 27](27-test-runner.md) · [doc 30](30-consistency-audit.md) · [doc 33](33-codebase-access-audit.md) · [doc 34](34-lessons-learned.md)

---

## Setup

Toolchain installer/verifier. Respeta `JAVA_HOME` y acepta cualquier Java >= 21 ([doc 26](26-java-version-compat-2026.md)).

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `setup` | Instalación interactiva con confirmación por grupo | `./nx setup` |
| `setup --verify` | Chequea toolchain. NO modifica nada. Exit 1 si falta algo | `./nx setup --verify` |
| `setup --upgrade` | Instala lo que falta y actualiza outdated | `./nx setup --upgrade` |
| `setup --dry-run` | Solo reporta qué haría, sin tocar el sistema | `./nx setup --dry-run` |
| `setup --yes` | Modo no interactivo (CI) | `./nx setup --yes` |
| `setup --only <grupos>` | Instalar solo grupos comma-separated | `./nx setup --only core,languages` |
| `setup --skip <grupos>` | Saltear grupos | `./nx setup --skip optional` |

Grupos: `core, languages, containers, kubernetes, aws, streaming, observability, optional`.

---

## Tests

Corredor paralelo con throttling de recursos. Grupos definidos en `.ai/test-groups.yaml`. Detalle completo: [doc 27](27-test-runner.md).

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `test` / `test --list` | Lista todos los grupos y composites disponibles | `./nx test --list` |
| `test --composite <name>` | Corre un composite (ver tabla abajo) | `./nx test --composite quick` |
| `test --group <name>` | Corre un grupo único | `./nx test --group unit` |
| `test --groups <a,b,c>` | Corre varios grupos en una sola corrida | `./nx test --groups unit,arch` |
| `test <name>` | Atajo backward-compatible para `--group <name>` | `./nx test atdd-cucumber` |
| `test <x> --dry-run` | Imprime el plan de ejecución sin correr | `./nx test --composite ci-full --dry-run` |
| `test <x> --auto-infra` | Levanta `docker compose` si algún grupo lo necesita | `./nx test --composite ci-full --auto-infra` |
| `test <x> --with-infra-compose` | Levanta compose antes y baja al terminar | `./nx test --composite ci-full --with-infra-compose` |
| `test --parallel N` / `--auto-parallel` | Limita o auto-detecta jobs concurrentes | `./nx test --composite ci-fast --auto-parallel` |
| `test --max-cpu PCT` / `--max-ram MB` | Tope de CPU/RAM reservada | `./nx test --max-cpu 80 --max-ram 8000` |
| `test --coverage` | Genera `jacocoAggregateReport`, copia HTML a `out/coverage/aggregate/` | `./nx test --coverage` |
| `test --json` | Salida estructurada para CI | `./nx test --composite ci-fast --json` |

### Composites disponibles

| Composite | Uso | Grupos incluidos |
|-----------|-----|------------------|
| `quick` | Demo live rápido | `quick-check` |
| `unit-sdk` | Unit tests de SDKs sin infra | `unit-sdk-java`, `unit-sdk-typescript`, `unit-sdk-go` |
| `integration-compose` | Suites que usan compose/stack compartido | `integration-compose-*` en modo exclusivo |
| `sdk-integration` | Integración de SDKs contra compose | `sdk-java-integration`, `sdk-typescript-integration`, `sdk-go-integration` |
| `ci-fast` | CI rápido sin Docker obligatorio | `unit-java-fast`, `arch`, `unit-sdk-*` |
| `ci-full` | CI completo de aplicación + SDK + contract + k6 smoke | unit, arch, component, Testcontainers, compose, SDK integration, `contract`, `bench-inproc`, `k6` |
| `k8s` / `ci-k8s` | Infra/k8s/nightly | `k8s-smoke`, `k8s-canary`, `k8s-bluegreen`, `k8s-rolling`, `k8s-argocd`, `k8s-eso` |
| `all` | Todo lo no-k8s + bench distribuido + coverage | `ci-full` + `bench-distributed` + `coverage-audit` |

### Matriz recomendada por contexto

| Contexto | Comando | Intención |
|----------|---------|-----------|
| Demo live | `./nx test --composite quick` | Guardrail sin Gradle/JUnit: boundaries de código + aviso de artefactos. |
| Pre-push | `./nx test --composite ci-fast` | Unit Java real + ArchUnit + SDK unit, sin levantar infra. |
| CI fast | `./nx test --composite ci-fast --json` | Feedback barato, SKIP grácil si falta algún tool opcional. |
| CI full | `./nx test --composite ci-full --with-infra-compose --json` | Aplicación completa, Testcontainers/compose, SDK integration, contract y k6 smoke. |
| Infra/k8s/nightly | `./nx test --composite k8s --with-infra-k8s --json` | Validación de rollouts/Argo/ESO sobre cluster local. |

### Grupos atómicos (selección)

`unit-java-fast`, `arch`, `unit-sdk-java`, `unit-sdk-typescript`, `unit-sdk-go`, `component-vertx`, `integration-testcontainers`, `integration-compose-vertx-atdd`, `integration-compose-smoke`, `integration-compose-monolith-unit`, `integration-compose-monolith-atdd`, `integration-compose-vertx-platform-atdd`, `sdk-java-integration`, `sdk-typescript-integration`, `sdk-go-integration`, `contract`, `k6`, `k8s-*`, `bench-inproc`, `bench-distributed`, `coverage-audit`.

### `./nx test k8s-*` (cluster local requerido)

Estos grupos requieren `./nx up k8s` previamente (k3d u OrbStack k8s). Ver
[doc 36](36-k8s-deployment-tests.md).

| Grupo | Cubre | Duración aprox. |
|-------|-------|-----------------|
| `k8s-smoke` | helm install + pods Ready + `/healthz` | 1 min |
| `k8s-canary` | Canary 20→50→100 + rollback automático | 6 min |
| `k8s-bluegreen` | activeService swap blue→green | 3 min |
| `k8s-rolling` | RollingUpdate zero-downtime | 3 min |
| `k8s-argocd` | Application Synced + Healthy | 1 min |
| `k8s-eso` | ExternalSecret → Secret nativo | 2 min |
| `k8s-rollouts` | Toda la suite k8s | 12 min |

### Skip por toolchain faltante

Cada grupo declara en `.ai/test-groups.yaml` el campo `requires:` con las CLIs necesarias (`docker`, `fnm`, `npm`, `node`, `go`). Si falta alguna, el runner emite SKIP con la razón en lugar de fallar con exit 127. Sintaxis OR soportada: `requires: ["fnm|npm"]` (cumple cualquiera). Ver [doc 27](27-test-runner.md).

---

## Performance positioning

- [`37-java-go-performance-positioning.md`](37-java-go-performance-positioning.md) — Java moderno vs Go: performance, concurrencia y trade-offs respaldados por fuentes primarias.
- [`38-java-apps-architecture-performance-matrix.md`](38-java-apps-architecture-performance-matrix.md) — Matriz de apps Java: misma lógica conceptual, distintos stacks/topologías, beneficios de Vert.x y gaps de benchmark.

## Audit

Auditorías de cobertura, consistencia y confidencialidad.

| Subcomando | Descripción | Doc |
|------------|-------------|-----|
| `audit consistency` | Auditoría completa de docs (incluye `prohibited-terms`) | [doc 30](30-consistency-audit.md) |
| `audit consistency <sub>` | Sub-audit puntual: `inventory`, `orphans`, `qa-coverage`, `xrefs`, `stale`, `terms`, `prohibited-terms` | [doc 30](30-consistency-audit.md) |
| `audit confidentiality` | Scan de términos prohibidos via SHA-256 (sin retener plaintext) | — |
| `audit codebase-access` | Ratio de cobertura de primitivas usadas vs definidas | [doc 33](33-codebase-access-audit.md) |
| `audit docs` | Cobertura de documentación | — |
| `audit cli` | Cobertura de scripts CLI | — |
| `audit primitives` | Cobertura de primitivas IA | — |
| `audit coverage` | `jacocoAggregateReport` + abre HTML | — |
| `audit all` | Cobertura agregada cross-suite | — |
| `audit --report-md` | Reporte Markdown | — |
| `audit --strict` | Exit 1 si overall < umbral | — |

Ejemplos:

```bash
./nx audit consistency
./nx audit confidentiality
./nx audit codebase-access --strict --threshold 70
./nx audit all --report-md
```

---

## Demo

Tráfico sintético contra los servicios vivos.

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `demo rest` | Corre el demo completo (todos los flujos REST) | `./nx demo rest` |
| `demo rest --amount N --customer X` | POST `/risk` único targeted | `./nx demo rest --amount 150000 --customer cust-001` |
| `demo websocket` | Conecta a endpoint WS via `wscat` | `./nx demo websocket` |
| `demo sse` | Consume el stream SSE via `curl` | `./nx demo sse` |
| `demo webhook` | Registra webhook y dispara | `./nx demo webhook` |
| `demo kafka` | Produce y consume mensaje en Kafka/Redpanda | `./nx demo kafka` |

---

## Bench

JMH y carga distribuida. Resolución dinámica del jar (no hardcodea versión).

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `bench inproc` | JMH in-process (sin red) | `./nx bench inproc` |
| `bench inproc -wi N -i N -f N` | Custom warmup / iterations / forks | `./nx bench inproc -wi 3 -i 5 -f 1` |
| `bench distributed` | Generación de carga HTTP contra server vivo | `./nx bench distributed` |
| `bench competition` | Ambos benchmarks comparados | `./nx bench competition` |

### k6 (Grafana load testing)

`./nx bench k6 <scenario> [--target SVC] [--vus N] [--duration T]`

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `bench k6 smoke` | 1 VU, 30s — el servicio arranca | `./nx bench k6 smoke --target bare` |
| `bench k6 load` | 32 VUs, 2 min — sustained, gate p99 < 300ms | `./nx bench k6 load --target distributed` |
| `bench k6 stress` | Ramp 0→100 VUs en 5 min — find the knee | `./nx bench k6 stress --target vertx-platform` |
| `bench k6 spike` | 0→200 VUs en 30s, hold 1m — burst tolerance | `./nx bench k6 spike --target distributed` |
| `bench k6 soak` | 16 VUs, 30 min — leak detection | `./nx bench k6 soak --target distributed` |
| `bench k6 competition <scenario>` | Mismo scenario contra los 4 servicios + comparison.md | `./nx bench k6 competition load` |

Targets: `bare` (8081), `monolith` (8090), `vertx-platform` (8180), `distributed` (8080).
Push a OpenObserve: `export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:5080/api/prom/push` antes del run.
Detalle: [bench/k6/README.md](../bench/k6/README.md) y [ADR-0040](../vault/02-Decisions/0040-k6-for-load-testing.md).

---

## Up / Down (infra y servicios)

| Subcomando | Descripción |
|------------|-------------|
| `up infra` | Solo infra compartida (Postgres + Valkey + Redpanda + observability) |
| `up vertx` | Infra + `java-vertx-distributed`. Healthcheck OpenBao via `bao status` |
| `up vertx-platform` | Infra + `vertx-risk-platform` |
| `up monolith` | Infra + `java-monolith` |
| `up all` | Infra + vertx + monolith + vertx-platform side-by-side |
| `up k8s` | Cluster k3d + helm install (`--orbstack` o `--k3d`) |
| `down <target>` | Bajada equivalente. Acepta los mismos targets que `up` |
| `down k8s --cleanup-k8s` | Destruye cluster y volúmenes |

```bash
./nx up vertx
./nx up k8s --orbstack
./nx down all
```

---

## Logs / Debug / Failures

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `logs --service <name>` | Tail filtrado por servicio | `./nx logs --service controller-app` |
| `logs --errors` | Solo ERROR/WARN/Exception | `./nx logs --errors` |
| `logs --grep <pat>` | Filtra por regex | `./nx logs --grep "5\\d\\d"` |
| `logs --correlation-id <id>` | Sigue una trace única cross-service | `./nx logs --correlation-id abc-123` |
| `logs --since <dur>` | Ventana temporal (ej `30m`, `2h`) | `./nx logs --since 1h` |
| `debug diagnose` | Heurística de root cause | `./nx debug diagnose` |
| `debug snapshot` | Bundle forensic a `out/debug/<ts>/` | `./nx debug snapshot` |
| `debug trace <id>` | Lookup de trace en OpenObserve | `./nx debug trace abc-123` |
| `debug probe` | Probe DNS/conectividad entre servicios | `./nx debug probe` |
| `failures` | Resumen unificado de fallas (Surefire + Cucumber + Karate + smoke) | `./nx failures` |
| `failures --suite <s>` | Solo una suite | `./nx failures --suite atdd-karate` |
| `failures --json` | Salida JSON para CI | `./nx failures --json` |
| `failures --since <dur>` | Solo runs recientes | `./nx failures --since 1d` |

---

## Admin (rules engine)

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `admin rules list` | Lista reglas activas | `./nx admin rules list` |
| `admin rules reload` | Hot-reload desde config | `./nx admin rules reload` |
| `admin rules test` | Prueba transacción de muestra | `./nx admin rules test` |

---

## Otros

| Subcomando | Descripción | Ejemplo |
|------------|-------------|---------|
| `build [target] [--rebuild]` | Smart incremental build (jars cacheados). Targets: `monolith`, `vertx`, `vrp`, `bare-javac`, `bench-distributed`, `bench-inprocess`, `all`. | `./nx build monolith` |
| `run risk-engine [--port N]` | Bare-javac HTTP risk engine | `./nx run risk-engine --port 8080` |
| `run vertx` / `run vertx-platform` / `run java-monolith` / `run k8s` | Arranca un stack puntual | `./nx run vertx` |
| `status` | Containers docker + pods k8s corriendo | `./nx status` |
| `dashboard up` / `dashboard down` | Homer en `localhost:8888` | `./nx dashboard up` |
| `scrub` | Scan de secrets/PII. Excluye `.ai/blocklist.sha256` y mocks declarados en `compose/` | `./nx scrub` |
| `version` | Imprime `$NX_VERSION` | `./nx version` |
| `help [<cmd>]` | Uso completo o por subcomando | `./nx help test` |

---

## Convenciones de output

Todos los scripts escriben en `out/<name>/<timestamp>/`. El symlink `latest` apunta a la corrida más reciente.

| Comando | Dir de output |
|---------|---------------|
| `./nx test --composite <x>` | `out/test-runner/latest/` |
| `./nx test --coverage` | `out/coverage/aggregate/` |
| `./nx audit consistency` | `out/audit-consistency/latest/` |
| `./nx audit codebase-access` | `out/codebase-access-audit/latest/` |
| `./nx bench inproc` | `out/bench-inprocess/latest/` |
| `./nx bench k6 <scenario>` | `out/k6/<scenario>/latest/` |
| `./nx bench k6 competition <scenario>` | `out/k6-competition/latest/` |
| `./nx debug snapshot` | `out/debug/<ts>/` |
| `./nx up vertx` | `out/vertx-up/latest/` |
| `./nx up k8s` | `out/k8s-up/latest/` |

Lecciones aprendidas durante Phase 6 + Phase 9: ver [doc 34](34-lessons-learned.md).

---

## Build performance

Default fast path: `./nx build [target]` hace **incremental** y **skip si los jars son frescos** (newer than sources). Sin contention de Gradle daemons cuando múltiples agentes corren en paralelo (lock por target en `.gradle/.nx-build-locks/`).

| Caso | Comando | Tiempo típico |
|------|---------|---------------|
| Cold build (sin jars) | `./nx build monolith` | 3–5 min |
| Warm (no source changes) | `./nx build monolith` | < 2 s (skip) |
| Incremental (1 archivo cambió) | `./nx build monolith` | 15–30 s |
| Force rebuild | `NX_REBUILD=1 ./nx build monolith` ó `./nx build monolith --rebuild` | 3–5 min |
| Legacy `gradle clean build` | `./nx build --legacy-clean` | 5–8 min (CI) |

Bench scripts (`bench/scripts/run-comparison.sh`) skipean Gradle si los jars existen. Forzar con `REBUILD=1` ó `--rebuild`.

Limpiar caches:
```bash
./gradlew --stop
rm -rf ~/.gradle/caches/build-cache-*
rm -rf .gradle/.nx-build-locks/
```

Concurrency guard: si dos agentes lanzan `./nx build monolith` simultáneamente, el segundo espera al primero (lockfile con PID-liveness check). Para fallar rápido en vez de esperar: `NX_BUILD_NOWAIT=1 ./nx build monolith`.
