# 00 — Empezá acá

Si llegaste desde el README raíz, ya tenés el pitch. Esta es la guía detallada.

## Qué hay en este repo

Una exploración técnica de un use case de detección de fraude productivo, que demuestra:

- varias PoCs en Java con funcionalidad comparable pero arquitecturas distintas (para argumentar trade-offs con números).
- Stack 2026: Java 21, Gradle Kotlin DSL multi-módulo, Vert.x 5, cluster Hazelcast.
- Observabilidad completa vía OTel + OpenObserve.
- AWS encapsulado localmente con Floci (un solo contenedor, MIT, ADR-0042) — sin LocalStack (sunset en marzo 2026).
- 3 SDKs cliente (Java/TS/Go) que encapsulan 7 canales de comunicación.
- CLI maestra `./nx` que orquesta todo.
- Sistema de primitivas IDE-agnóstico (`.ai/`) para que cualquier agente IA (Claude Code, Cursor, Windsurf, Copilot, etc.) pueda trabajar sin leer el código fuente.
- ATDD (Karate + Cucumber), benchmarks (JMH), tests de integración (Testcontainers), tests de arquitectura (ArchUnit).

---

## Qué leer primero (orden recomendado)

### Si tu objetivo es entender la arquitectura

1. **`docs/12-rendimiento-y-separacion.md`** — comparación de las 3 arquitecturas con números reales. La pieza central.
2. **`docs/38-java-apps-architecture-performance-matrix.md`** — matriz de apps Java: misma lógica, distintas topologías/stacks y beneficios de Vert.x.
3. **[`docs/39-share-ready-baseline.md`](39-share-ready-baseline.md)** — anchor público, auditabilidad histórica y checks pre-tag.
4. **`docs/37-java-go-performance-positioning.md`** — investigación con fuentes primarias: Java moderno vs Go en performance/concurrencia.
5. **`docs/04-clean-architecture-java.md`** — Clean Architecture aplicada a Java enterprise.
6. **`docs/13-paridad-logica-poc.md`** — qué cambia y qué NO cambia entre las PoCs.
7. **`vault/02-Decisions/`** — 37 ADRs con análisis de alternativas Opción A/B/C/D.

### Si tu objetivo es entender el enfoque de testing

1. **`docs/11-atdd.md`** — filosofía ATDD + Karate vs Cucumber.
2. **`docs/20-business-rules-test-plan.md`** — 68 casos de prueba para el motor de reglas.
3. **`docs/21-meta-coverage.md`** — meta-cobertura: docs, CLI, primitivas más allá de los tests.

### Si tu objetivo es metodología de diseño

1. **`docs/01-design-conversation-framework.md`** — cómo descomponer problemas de systems design.
2. **`docs/09-architecture-question-bank.md`** — 25+ preguntas de arquitectura con análisis modelo y modos de falla comunes.
3. **`vault/05-Methodology/Architectural-Anchors.md`** — principios de diseño que pesan.

### Si te interesa el meta (agentes IA + tooling)

1. **`.ai/context/agent-os-principles.md`** — 8 principios de orquestación de agentes IA.
2. **`docs/22-client-sdks.md`** — diseño multi-lenguaje + SemVer.
3. **`docs/27-test-runner.md`** — test runner con DAG + throttling de recursos.
4. **`.ai/scripts/quick-check.py`** — guardrail sub-segundo de demo: boundaries fuente + freshness warnings sin invocar Gradle.
4. **`.ai/scripts/consistency-auditor.py`** + **`.ai/audit-rules/terminology.yaml`** — meta-cobertura de docs (la spec vive en el script).

---

## Quickstart funcional (5 comandos)

```bash
# 1. Toolchain (detecta lo que ya tenés, instala lo que falta)
./nx setup

# 2. Levantar infra + apps Vert.x (postgres, valkey, tansu, openobserve, AWS mocks, 4 apps Java)
./nx up vertx-layer-as-pod-eventbus

# 3. Demo: request de prueba POST /risk
./nx demo rest --amount 150000

# 4. Tests rápidos (sin infra) + opcionalmente suite completa
./nx test --composite quick
./nx test --composite ci-full --with-infra-compose   # completo

# 5. Dashboard con todos los paneles
./nx dashboard up
```

Output esperado: tabla de tests + `http://localhost:8888` (dashboard Homer) con links a OpenObserve, Tansu broker, Floci (`:4566`), etc.

---

## PoCs explicadas por eje Vert.x

```text
SIN VERT.X
  poc/no-vertx-clean-engine/
    -> ¿cuánto cuesta la lógica pura?

CON VERT.X
  poc/vertx-monolith-inprocess/
    -> ¿qué aporta Vert.x sin distribuir?

  poc/vertx-layer-as-pod-eventbus/
    -> ¿qué pasa si separo layers por pods usando EventBus clustered?

  poc/vertx-layer-as-pod-http/
    -> ¿qué pasa si separo layers por pods usando HTTP + tokens?

  poc/vertx-service-mesh-bounded-contexts/
    -> ¿qué pasa si separo por servicios/bounded contexts reales?
```

| Característica | no-vertx-clean-engine | vertx-monolith-inprocess | vertx-layer-as-pod-eventbus |
|---|---|---|---|
| HTTP API | puerto 8081, stdlib | puerto 8090, Vert.x | puerto 8080, Vert.x |
| Persistencia real | in-memory | Postgres + Valkey | Postgres + Valkey (vía repo-app) |
| Publicación Kafka | outbox in-memory | Real (Tansu) | Real (Tansu) |
| S3/SQS/Secrets | adapters NoOp | Real (Floci unified emulator) | Real (mismo) |
| Frameworks externos | ninguno | Vert.x + AWS SDK + JDBC + Lettuce | igual + cluster Hazelcast |
| Layering físico | métodos in-process | verticles in-process | 3 JVMs en path síncrono vía EventBus + consumer async Kafka |
| Latencia esperada | la más baja | la más baja (igual a no-vertx-clean-engine) | mayor (overhead de red/serialización) |
| Aislamiento | mínimo (todo falla junto) | mínimo | máximo (blast radius por capa) |
| Escalado | 1 JVM x N réplicas | 1 JVM x N réplicas | independiente por capa |

---

## Estado verificado vs aspiracional

> Para el snapshot empírico completo con números de runs reales, ver [`STATUS.md`](../STATUS.md) en la raíz del repo.

| Componente | Estado |
|---|---|
| Cucumber ATDD no-vertx-clean-engine | Verificado, pasa — 10 escenarios, cobertura 51.8% líneas |
| JMH bench in-process | Verificado — p50=125ns, p99=459ns |
| BenchmarkRunner virtual threads | Verificado — 1528 req/s |
| Builds Gradle | `BUILD SUCCESSFUL` reportado por agente — pendiente verificación humana |
| Karate ATDD distribuido | Diseñado (31 escenarios + JaCoCo cross-module) — pendiente docker compose up |
| Tests de integración Testcontainers | Diseñados (11 en main + 31 SDK) — pendiente correrlos |
| End-to-end full stack | Pendiente — último ítem en la cola actual |

Honestidad: el repo está en estado *reportado-OK por agentes pero verificación-empírica-humana pendiente*. El próximo paso del proyecto es esa verificación.

---

## Si algo se rompe

```bash
./nx status                        # qué contenedores están arriba
./nx logs --service usecase-app    # logs filtrables
./nx logs --errors                 # solo ERROR/WARN/Exception
./nx debug diagnose                # heurística automática de root cause
./nx debug snapshot                # bundle forense en out/debug/<ts>/
./nx failures                      # resumen unificado de tests fallando
./nx audit consistency             # xrefs rotos, huérfanos, inconsistencias de términos
```

---

## Estructura del repo (alto nivel)

```
real-time-risk-lab/
├── README.md                  # pitch + tour por tiempo disponible
├── docs/                      # 36 docs numerados (00-31+)
├── vault/                     # vault Obsidian — 37 ADRs + conceptos + build logs
├── poc/
│   ├── no-vertx-clean-engine/      # no-vertx-clean-engine
│   ├── vertx-monolith-inprocess/         # single JVM Vert.x
│   ├── vertx-layer-as-pod-eventbus/# 3 JVMs EventBus + consumer async Kafka
│   ├── k8s-local/             # k3d + helm + addons
│   ├── vertx-layer-as-pod-http/ # 3 pods HTTP + tokens
│   └── vertx-service-mesh-bounded-contexts/ # service-to-service Vert.x
├── pkg/                       # librerías compartidas (errors, resilience, events, kafka, observability, repositories, etc)
├── sdks/
│   ├── risk-events/           # contratos de eventos compartidos
│   ├── risk-client-java/      # SDK Java
│   ├── risk-client-typescript/# SDK TypeScript
│   ├── risk-client-go/        # SDK Go
│   └── contract-test/         # contract test cross-SDK
├── compose/                   # Docker compose base + override dev-tools
├── bench/                     # JMH inproc + load gen HTTP + competition
├── tests/
│   ├── architecture/          # ArchUnit
│   ├── risk-engine-atdd/      # Cucumber ATDD
│   └── integration/           # Testcontainers
├── cli/risk-smoke/            # smoke runner Go TUI
├── dashboard/                 # config Homer
├── examples/rules-config/     # samples YAML (v1, v2, v3-broken)
├── scripts/                   # setup, test-all, lib helpers
├── .ai/                       # primitivas (skills/rules/workflows/hooks) + adapters por IDE
├── nx                         # CLI maestra
└── BUILDING.md                # reactor multi-módulo Gradle explicado
```

---

## Comandos por categoría

Ver `docs/QUICK-REFERENCE.md` para la tabla completa. Resumen:

| Categoría | Comando |
|---|---|
| Setup | `./nx setup [--verify\|--upgrade\|--dry-run]` |
| Infra | `./nx up {infra\|vertx-layer-as-pod-eventbus\|vertx-monolith-inprocess\|vertx-layer-as-pod-http\|all\|k8s}` / `./nx down ...` |
| Apps | `./nx run {no-vertx-clean-engine\|vertx-monolith-inprocess\|vertx-layer-as-pod-eventbus\|vertx-layer-as-pod-http}` |
| Tests | `./nx test --list\|--group X\|--composite Y\|--coverage\|--auto-fix` |
| Bench | `./nx bench {inproc\|vertx-layer-as-pod-eventbus\|competition\|k6}` |
| Demo | `./nx demo {rest\|websocket\|sse\|webhook\|kafka}` |
| Dashboard | `./nx dashboard {up\|down}` |
| Admin | `./nx admin rules {list\|reload\|test}` |
| Audit | `./nx audit {docs\|cli\|primitives\|consistency\|coverage\|confidentiality\|all}` |
| Debug | `./nx logs [filtros]` / `./nx debug {diagnose\|snapshot\|trace\|probe}` / `./nx failures` |

---

## Para reviewers técnicos

Si querés compartir esto con un reviewer externo:

1. Primero, corré `./nx test --composite ci-full --with-infra-compose` y verificá que todo pasa. Hoy hay reportes de agente pero no verificación humana.
3. Si el reviewer tiene menos de 30 minutos, dirigilo al tour de 5 minutos del README raíz.
4. Para reviewers técnicos serios: `vault/02-Decisions/_index.md` + `docs/12-rendimiento-y-separacion.md` son los entry points fuertes.

---

## Principio de cierre

> "La cobertura de tests es lo que casi todos miden. La cobertura de docs, la cobertura de CLI y la cobertura de primitivas es lo que distingue un sistema mantenible de uno frágil. Cuando entrás a un repo nuevo, los primeros tres son los que revelan si vas a poder operarlo o sufrirlo."

---

## Índice operativo para auditoría de consistencia

Esta sección existe para que el auditor documental tenga referencias explícitas a módulos, documentos y scripts que son parte del inventario curado.

### Módulos Gradle inventariados

- `poc:no-vertx-clean-engine`
- `poc:vertx-monolith-inprocess`
- `poc:vertx-monolith-inprocess:atdd-tests`
- `poc:vertx-layer-as-pod-eventbus:shared`
- `poc:vertx-layer-as-pod-eventbus:controller-app`
- `poc:vertx-layer-as-pod-eventbus:usecase-app`
- `poc:vertx-layer-as-pod-eventbus:repository-app`
- `poc:vertx-layer-as-pod-eventbus:consumer-app`
- `poc:vertx-service-mesh-bounded-contexts:shared`
- `poc:vertx-service-mesh-bounded-contexts:risk-decision-service`
- `poc:vertx-service-mesh-bounded-contexts:fraud-rules-service`
- `poc:vertx-service-mesh-bounded-contexts:ml-scorer-service`
- `poc:vertx-service-mesh-bounded-contexts:audit-service`
- `poc:vertx-layer-as-pod-http`
- `poc:vertx-layer-as-pod-http:atdd-tests`
- `tests:risk-engine-atdd`
- `tests:architecture`
- `bench:distributed-bench`
- `bench:runner`

### Documentos secundarios incluidos en el mapa público

- `docs/06-vertx-pods-locales.md`
- `docs/07-technical-leadership-design-mindset.md`
- `docs/08-technical-discussion-simulation.md`
- `docs/14-primitive-usage-retro.md`
- `docs/17-decision-stack-observability-local.md`
- `docs/18-rules-engine-design.md`
- `docs/19-backoffice-simulation-design.md`
- `docs/32-failure-debug-toolkit.md`
- `docs/35-runbook-demo-fails.md`

### Scripts y tests auxiliares

- `scripts/TESTING.md`
- `scripts/diagnose-saturation.sh`
- `scripts/nx-completion.bash`
- `scripts/test_nx.sh`
- `scripts/test_test_runner.py`
- `.ai/scripts/test_agent_bus.py`
- `.ai/scripts/test_consistency_auditor.py`
- `.ai/scripts/test_workflow_runner.py`
