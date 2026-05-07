# 00 — Empezá acá

Si llegaste desde el README raíz, ya tenés el pitch. Esta es la guía detallada.

## Qué hay en este repo

Una exploración técnica de un use case de detección de fraude productivo, que demuestra:

- 3 PoCs en Java con la misma funcionalidad pero arquitecturas distintas (para argumentar trade-offs reales con números).
- Stack 2026: Java 21, Gradle Kotlin DSL multi-módulo, Vert.x 5, cluster Hazelcast.
- Observabilidad completa vía OTel + OpenObserve.
- AWS encapsulado localmente (MinIO, ElasticMQ, Moto, OpenBao) — sin LocalStack.
- 3 SDKs cliente (Java/TS/Go) que encapsulan 7 canales de comunicación.
- CLI maestra `./nx` que orquesta todo.
- Sistema de primitivas IDE-agnóstico (`.ai/`) para que cualquier agente IA (Claude Code, Cursor, Windsurf, Copilot, etc.) pueda trabajar sin leer el código fuente.
- ATDD (Karate + Cucumber), benchmarks (JMH), tests de integración (Testcontainers), tests de arquitectura (ArchUnit).

---

## Qué leer primero (orden recomendado)

### Si tu objetivo es entender la arquitectura

1. **`docs/12-rendimiento-y-separacion.md`** — comparación de las 3 arquitecturas con números reales. La pieza central.
2. **`docs/04-clean-architecture-java.md`** — Clean Architecture aplicada a Java enterprise.
3. **`docs/13-paridad-logica-poc.md`** — qué cambia y qué NO cambia entre las 3 PoCs.
4. **`vault/02-Decisions/`** — 37 ADRs con análisis de alternativas Opción A/B/C/D.

### Si tu objetivo es entender el enfoque de testing

1. **`docs/11-atdd.md`** — filosofía ATDD + Karate vs Cucumber.
2. **`docs/20-business-rules-test-plan.md`** — 68 casos de prueba para el motor de reglas.
3. **`docs/21-meta-coverage.md`** — meta-cobertura: docs, CLI, primitivas más allá de los tests.

### Si tu objetivo es metodología de diseño

1. **`docs/01-design-conversation-framework.md`** — cómo descomponer problemas de systems design.
2. **`docs/09-architecture-question-bank.md`** — 25+ preguntas de arquitectura con análisis modelo y modos de falla comunes.
3. **`vault/05-Methodology/Architectural-Anchors.md`** — principios de diseño que pesan.

### Si te interesa el meta (agentes IA + tooling)

1. **`docs/16-agent-os-principles.md`** — 8 principios de orquestación de agentes IA.
2. **`docs/22-client-sdks.md`** — diseño multi-lenguaje + SemVer.
3. **`docs/27-test-runner.md`** — test runner con DAG + throttling de recursos.
4. **`docs/30-consistency-audit.md`** — meta-cobertura de docs.

---

## Quickstart funcional (5 comandos)

```bash
# 1. Toolchain (detecta lo que ya tenés, instala lo que falta)
./nx setup

# 2. Levantar infra + apps Vert.x (postgres, valkey, redpanda, openobserve, AWS mocks, 4 apps Java)
./nx up vertx

# 3. Demo: request de prueba POST /risk
./nx demo rest --amount 150000

# 4. Tests rápidos (sin infra) + opcionalmente suite completa
./nx test --composite quick
./nx test --composite ci-full --with-infra-compose   # completo

# 5. Dashboard con todos los paneles
./nx dashboard up
```

Output esperado: tabla de tests + `http://localhost:8888` (dashboard Homer) con links a OpenObserve, Redpanda Console, consola MinIO, etc.

---

## Las tres PoCs explicadas

```
poc/java-risk-engine/         # Bare-javac, in-memory, sin frameworks externos
poc/java-monolith/            # Single JVM Vert.x, infra real (Postgres+Kafka+S3+...)
poc/java-vertx-distributed/   # 4 JVMs Vert.x separadas, cluster Hazelcast TCP
```

| Característica | bare-javac | java-monolith | java-vertx-distributed |
|---|---|---|---|
| HTTP API | puerto 8081, stdlib | puerto 8090, Vert.x | puerto 8080, Vert.x |
| Persistencia real | in-memory | Postgres + Valkey | Postgres + Valkey (vía repo-app) |
| Publicación Kafka | outbox in-memory | Real (Redpanda) | Real (Redpanda) |
| S3/SQS/Secrets | adapters NoOp | Real (MinIO/ElasticMQ/Moto) | Real (mismo) |
| Frameworks externos | ninguno | Vert.x + AWS SDK + JDBC + Lettuce | igual + cluster Hazelcast |
| Layering físico | métodos in-process | verticles in-process | 4 JVMs + event bus distribuido |
| Latencia esperada | la más baja | la más baja (igual a bare) | mayor (overhead de red/serialización) |
| Aislamiento | mínimo (todo falla junto) | mínimo | máximo (blast radius por capa) |
| Escalado | 1 JVM x N réplicas | 1 JVM x N réplicas | independiente por capa |

---

## Estado verificado vs aspiracional

> Para el snapshot empírico completo con números de runs reales, ver [`STATUS.md`](../STATUS.md) en la raíz del repo.

| Componente | Estado |
|---|---|
| Cucumber ATDD bare-javac | Verificado, pasa — 10 escenarios, cobertura 51.8% líneas |
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
risk-decision-platform/
├── README.md                  # pitch + tour por tiempo disponible
├── docs/                      # 36 docs numerados (00-31+)
├── vault/                     # vault Obsidian — 37 ADRs + conceptos + build logs
├── poc/
│   ├── java-risk-engine/      # bare-javac
│   ├── java-monolith/         # single JVM Vert.x
│   ├── java-vertx-distributed/# 4 JVMs Vert.x
│   ├── k8s-local/             # k3d + helm + addons
│   └── (vertx-risk-platform — legacy, no consumir)
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
| Infra | `./nx up {infra\|vertx\|monolith\|all\|k8s}` / `./nx down ...` |
| Apps | `./nx run {risk-engine\|java-monolith\|vertx}` |
| Tests | `./nx test --list\|--group X\|--composite Y\|--coverage\|--auto-fix` |
| Bench | `./nx bench {inproc\|distributed\|competition}` |
| Demo | `./nx demo {rest\|websocket\|sse\|webhook\|kafka}` |
| Dashboard | `./nx dashboard {up\|down}` |
| Admin | `./nx admin rules {list\|reload\|test}` |
| Audit | `./nx audit {docs\|cli\|primitives\|consistency\|coverage\|confidentiality\|all}` |
| Debug | `./nx logs [filtros]` / `./nx debug {diagnose\|snapshot\|trace\|probe}` / `./nx failures` |

---

## Para reviewers técnicos

Si querés compartir esto con un reviewer externo:

1. Primero, corré `./nx test --composite ci-full --with-infra-compose` y verificá que todo pasa. Hoy hay reportes de agente pero no verificación humana.
2. Considerá si `vertx-risk-platform` debería borrarse o archivarse — es legacy.
3. Si el reviewer tiene menos de 30 minutos, dirigilo al tour de 5 minutos del README raíz.
4. Para reviewers técnicos serios: `vault/02-Decisions/_index.md` + `docs/12-rendimiento-y-separacion.md` son los entry points fuertes.

---

## Principio de cierre

> "La cobertura de tests es lo que casi todos miden. La cobertura de docs, la cobertura de CLI y la cobertura de primitivas es lo que distingue un sistema mantenible de uno frágil. Cuando entrás a un repo nuevo, los primeros tres son los que revelan si vas a poder operarlo o sufrirlo."
