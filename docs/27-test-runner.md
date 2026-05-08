---
title: "27 -- Test runner paralelo inteligente"
tags: [testing, ci, automation, devops]
---

# 27 -- Test runner paralelo inteligente

## Por qué existe este runner

`scripts/test-all.sh` es un orquestador secuencial: corre cada suite una después de
otra, espera a que cada una termine y usa arrays asociativos de bash. Funciona pero
tiene dos limitaciones de fondo:

1. Ejecución secuencial: incluso suites independientes (unit, arch, tests de SDK) se
   bloquean entre sí. En una máquina de 8 cores corriendo `ci-fast`, perdés 4-5 minutos.
2. Sin awareness de recursos: si dos suites pesadas en memoria arrancan a la vez y agotan
   la RAM, el OS hace swap y ambas suites tardan 3x más.

`scripts/test-runner.py` resuelve ambos problemas con un scheduler DAG y throttling
de recursos, manteniendo backward compatibility total con la API existente
`./nx test <suite>`.

## Diseño: DAG + Scheduler + Throttle

### DAG

Los jobs se separan en niveles de dependencia:

- Nivel 0: jobs con `needs_infra: false` (sin servicios externos requeridos)
- Nivel 1+: jobs que dependen de infra compose/docker/k8s

Dentro de cada nivel, los jobs no exclusivos corren concurrentemente; los exclusivos
(benchmarks) corren en un sub-nivel de un solo job para evitar contención de CPU.

El topological sort es determinístico: el mismo input siempre produce el mismo plan
de ejecución, lo que hace el output del dry-run estable y útil para revisión del plan
en CI.

La ejecución usa un `ThreadPoolExecutor`: cada thread solo espera a un proceso hijo
(`gradlew`, `npm`, `go`, docker tools). Esto evita el overhead de `multiprocessing`
en macOS y reduce el riesgo de semáforos filtrados o bloqueos del runtime Python.

### Throttle de recursos

El scheduler trackea dos recursos a través de todos los jobs corriendo:

- `reserved_cpu_pct`: suma de `cost_cpu_pct` de todos los jobs activos
- `reserved_ram_mb`: suma de `cost_ram_mb` de todos los jobs activos

Antes de despachar un job, `_can_dispatch()` chequea:
```
reserved_cpu + job.cost_cpu <= max_cpu_pct
reserved_ram + job.cost_ram <= max_ram_mb
```

Un thread background `ResourceMonitor` refresca la carga real del sistema cada 2 segundos
usando `os.getloadavg()` (Mac/Linux) y `/proc/meminfo` (Linux) / `sysctl hw.memsize`
(Mac). Estas lecturas son informativas; el gate primario del scheduler es la contabilidad
de reservas.

Los jobs exclusivos (benchmarks, compose, contract, k6 y k8s) usan un flag `_exclusive_running`: cuando uno está
corriendo, ningún otro job puede despacharse, y un job exclusivo no arranca hasta que
no haya otros jobs corriendo.

Además, las invocaciones Gradle son **single-flight**: el runner no lanza dos
`./gradlew ...` al mismo tiempo. Esto evita tormentas de Gradle daemons,
contención sobre `.gradle/noVersion/buildLogic.lock` y congelamientos de laptop.
Mientras corre Gradle, solo pueden convivir jobs livianos no-Gradle si el límite
de recursos lo permite.

### Seguridad ante deadlock

Si todos los jobs pendientes exceden el headroom actual de recursos pero nada está
corriendo, el scheduler fuerza el dispatch del próximo job en lugar de deadlockear.
Esto cubre configuraciones donde el costo de un único job excede el techo configurado.

## Awareness de recursos explicado

Cada grupo en `.ai/test-groups.yaml` declara:

```yaml
unit-java-fast:
  cost_cpu: medium     # low=15%, medium=35%, high=70% de reserva de CPU
  cost_ram_mb: 800     # consumo pico estimado de RAM
  exclusive: false     # cuando es true, corre solo (sin jobs concurrentes)
  requires: ["fnm|npm"]  # tools externos requeridos en PATH (opcional). El
                          # separador `|` indica alternativas: cualquiera de las
                          # listadas satisface el requisito.
```

### Campo `requires`: SKIP-on-missing-tool

Algunos grupos dependen de CLIs externos que pueden no estar instalados en el host
(ej. `sdk-typescript` necesita `npm`/`node`, `sdk-go` necesita `go`, los grupos con
`needs_infra: compose|docker` necesitan `docker`). Antes, si la herramienta faltaba,
el job fallaba con `exit 127` (command not found) y contaba como FAIL en el reporte
de CI, ensuciando el verdict.

El campo `requires:` (lista flow-style YAML) declara las herramientas necesarias.
Antes de despachar un job, el runner ejecuta `shutil.which(tool)` para cada entrada;
si alguna falta, el job se marca como **SKIP** con un mensaje claro:

```
SKIP     sdk-typescript   (required tool 'npm' not found in PATH
                           (install it to enable sdk-typescript tests))
```

Comportamiento:

- Tool ausente → `SKIP`, no cuenta como fallo, exit code del runner = 0 si los demás pasan.
- Tool presente y comando OK → `PASS`.
- Tool presente y comando falla (ej. `npm install` rompe) → `FAIL` normal.

Los jobs SKIP aparecen en `summary.md`, `results.json` (status `SKIP`), y el output
de `--dry-run` los enumera bajo `SKIPPED (missing tools)`. Esto vuelve idempotente
el `--composite ci-full` en hosts mínimos: solo corre lo que puede correr y reporta
graciosamente lo que no.

Grupos que actualmente declaran `requires`:

| Grupo | requires |
|---|---|
| `unit-sdk-typescript`, `sdk-typescript`, `sdk-typescript-integration` | `["fnm\|npm"]` (+ `docker` para integration). El comando se envuelve en `scripts/lib/with-fnm.sh` para activar el entorno fnm si está presente. |
| `unit-sdk-go`, `sdk-go`, `sdk-go-integration` | `[go]` (+ `docker` para integration) |
| `integration-*`, `sdk-*-integration`, `contract`, `k6`, `bench-distributed`, aliases legacy compose/docker | `[docker]` (+ `k6` para `k6`) |

### Node.js via fnm

Este repo usa **fnm** (Fast Node Manager, https://github.com/Schniz/fnm) para
gestionar Node.js. Es como nvm pero escrito en Rust, switching automático con
`.node-version`. Razones:

- Multiples versiones de Node coexisten sin conflictos.
- `fnm use` automático al hacer `cd` a un dir con `.node-version` (con `--use-on-cd`).
- Activación por shell, no global; no rompe otros proyectos.

**Instalación:**

```bash
brew install fnm                   # macOS
# o:  curl -fsSL https://fnm.vercel.app/install | bash   # Linux

fnm install --lts                  # instala la última LTS
fnm default lts-latest             # la marca como default
```

**Activación en shell (~/.zshrc o ~/.bashrc):**

```bash
eval "$(fnm env --use-on-cd)"
```

Sin esa línea, `node` y `npm` no estarán en PATH aunque fnm esté instalado.

**Cómo lo usa el test-runner:** los grupos `sdk-typescript[-integration]` envuelven
su comando en `scripts/lib/with-fnm.sh`, que ejecuta `eval "$(fnm env)"` antes de
correr `npm install && npm test`. Si fnm no está pero `node`/`npm` están en PATH
(otro manager), el wrapper también funciona. Si nada está disponible, el grupo
hace SKIP graciosamente.

**Pinning por proyecto:** `sdks/risk-client-typescript/.node-version` fija la
versión usada (actualmente `22.14.0`). fnm la respeta automáticamente.

`--parallel` usa un default conservador de `2` para desarrollo local. `--auto-parallel`
es opt-in y deriva el máximo desde la cantidad de cores; usalo en CI o en una
máquina dedicada, no durante trabajo interactivo.

`--max-cpu` (default 80%) y `--max-ram` (default 80% de la RAM total del sistema) son
los techos. Con 4 SDKs costando cada uno 15% de CPU y 400MB de RAM en una máquina de 16GB:
los 4 corren concurrentemente sin tocar ningún techo.


## Taxonomía actual de suites

`quick` no significa “todo lo que Gradle llama test”: significa feedback mínimo confiable para demo live. No invoca Gradle/JUnit. Si querés unit + ArchUnit reales, usá `ci-fast`.

| Slice | Nombre | Infra | Nota |
|------|--------|-------|------|
| Guardrail live | `quick-check` | No | Python stdlib; source boundaries + aviso de artefactos, sin Gradle. |
| Unit Java rápido | `unit-java-fast` | No | Lista explícita de tareas Gradle; nunca `./gradlew test`. |
| Unit SDK | `unit-sdk` | No | Composite de Java/TypeScript/Go SDK unit. |
| Arquitectura | `arch` | No | ArchUnit; exclusivo para evitar carrera de reportes XML con otra invocación Gradle. |
| Component Vert.x | `component-vertx-layer-as-pod-http` | No | Tests in-process con puertos dinámicos. |
| Integration Testcontainers | `integration-testcontainers` | Docker | Corre solo por consumo de Docker/Ryuk. |
| Integration compose | `integration-compose` | Compose | Suites compose marcadas `exclusive: true`. |
| SDK integration | `sdk-integration` | Compose | SDKs contra stack local, uno por vez. |
| Contract | `contract` | Compose | Separado de SDK integration para poder gatearlo explícitamente. |
| Load smoke | `k6` | Compose + k6 | Smoke de k6, exclusivo. |
| Kubernetes | `k8s` / `ci-k8s` | k8s | Nightly/infra, exclusivo por suite. |

Matriz recomendada:

| Contexto | Comando |
|----------|---------|
| Demo live | `./nx test --composite quick` |
| Pre-push | `./nx test --composite ci-fast` |
| CI fast | `./nx test --composite ci-fast --json` |
| Full local no-k8s | `./nx test all --with-infra-compose` |
| CI full | `./nx test --composite ci-full --with-infra-compose --json --auto-parallel` |
| Infra/k8s/nightly | `./nx test --composite k8s --with-infra-k8s --json` |

## Referencia de CLI

```
./nx test                                   Lista grupos y composites
./nx test --list                            Igual que arriba
./nx test --group unit-java-fast            Corre un solo grupo
./nx test --groups unit-java-fast,arch     Corre dos grupos
./nx test --composite ci-fast              Corre un composite
./nx test --composite ci-fast --auto-parallel
./nx test --composite ci-fast --dry-run    Imprime solo el plan
./nx test --composite integration-compose --auto-infra  compose-up automático
./nx test --composite all --max-cpu 70 --max-ram 6000
./nx test --json                            Output JSON para CI
./nx test --parallel N                      Forzar N jobs concurrentes (default local: 2)
./nx test --out-dir /tmp/run-001           Output dir custom
```

Aliases backward-compatible (proxy hacia `--group`):
```
./nx test unit
./nx test smoke
./nx test atdd-karate
./nx test atdd-cucumber
./nx test integration
./nx test architecture
./nx test all
```

## Control de procesos

Para diagnosticar corridas colgadas o procesos huérfanos del runner, no uses
`ps | grep` manual. El wrapper repo-scoped es:

```bash
./nx proc status
./nx proc status --include-gradle-daemons
./nx proc stop                         # dry-run por defecto
./nx proc stop --only-kind test-runner --yes
./nx proc stop --include-gradle-daemons --signal TERM --yes
```

Principios:

- Por defecto solo muestra procesos cuyo comando referencia este repo, más sus
  descendientes vivos.
- Los Gradle daemons globales quedan fuera salvo que pases
  `--include-gradle-daemons`.
- `stop` es seguro por defecto: imprime qué mataría y exige `--yes`.
- La salida trunca comandos largos para que el diagnóstico sea legible; usá
  `--truncate 0` para verlos completos.

## Output

Cada corrida produce:
```
out/test-runner/<timestamp>/
  plan.json          Plan de ejecución (niveles, jobs, hints de recursos)
  summary.md         Tabla de resultados en Markdown
  results.json       Resultados machine-readable para CI
  job-<name>.log     stdout+stderr por job
out/test-runner/latest -> <timestamp>/   Symlink a la corrida más reciente
```

## Cómo agregar un grupo de tests nuevo

1. Abrir `.ai/test-groups.yaml`.
2. Agregar una entrada bajo `groups:`:
   ```yaml
   my-new-suite:
     cmd: "./gradlew :my-module:test"
     needs_infra: false
     cost_cpu: medium
     cost_ram_mb: 600
     duration_estimate_sec: 45
   ```
3. Opcionalmente agregarlo a un composite bajo `composites:`.
4. Correr `./nx test --list` para verificar que aparece.
5. Correr `./nx test --group my-new-suite --dry-run` para verificar el plan.

## Referencia de diseño

> "Un test runner que no modela restricciones de recursos corre tus tests en
> paralelo hasta que la máquina hace OOM y todas las suites fallan. Schedulear
> con awareness de recursos no es optimización prematura; es el baseline para CI
> reproducible. El DAG asegura que los tests dependientes de infra esperen a la
> infra; el throttle mantiene CPU y RAM dentro de límites seguros; el dry-run hace
> el plan auditable antes de correrlo."
