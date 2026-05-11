---
title: Índice de ADRs
tags: [index, decisions]
updated: 2026-05-07
---

# Índice de ADRs — Real-Time Risk Lab Architecture Exploration

Total de ADRs: 39

## Cómo leer

Cada ADR sigue el formato de Nygard con una sección "Alternativas consideradas" enriquecida que muestra al menos 3 opciones no triviales con ventajas/desventajas/racional. Valores de status: `accepted` (activo), `superseded by <ADR-id>` (reemplazado), `deprecated` (ya no aplica).

---

## Todas las decisiones

| ADR | Título | Status | Área | Fecha |
|-----|--------|--------|------|-------|
| [[0001-java-25-lts]] | Java 21 baseline operativo + Java 25 objetivo | accepted | runtime/java | 2026-05-07 |
| [[0002-enterprise-go-layout-in-java]] | Adoptar el layout enterprise Go en el PoC Java | accepted | architecture/layout | 2026-05-07 |
| [[0003-vertx-for-distributed-poc]] | Vert.x 5 con Hazelcast para el PoC distribuido | accepted | distributed/framework | 2026-05-07 |
| [[0004-openobserve-otel]] | OpenObserve como backend OTEL unificado | accepted | observability/otel | 2026-05-07 |
| [[0005-aws-mocks-stack]] | Stack curado de mocks AWS (anti-LocalStack) | accepted | aws/testing | 2026-05-07 |
| [[0006-atdd-karate-cucumber]] | ATDD dual — Karate + Cucumber-JVM | accepted | testing/atdd | 2026-05-07 |
| [[0007-k3d-orbstack-switch]] | k3d con autodetección del Docker context de OrbStack | accepted | infrastructure/local-dev | 2026-05-07 |
| [[0008-outbox-pattern-explicit]] | Outbox Pattern agregado explícitamente al PoC | accepted | events/reliability | 2026-05-07 |
| [[0009-bubbletea-tui-smoke]] | Go + Bubble Tea para el smoke runner TUI | accepted | tooling/go | 2026-05-07 |
| [[0010-ide-agnostic-primitives]] | Primitivas de IA agnósticas de IDE en .ai/ | accepted | tooling/ai | 2026-05-07 |
| [[0011-engram-mcp-memory]] | Engram MCP para memoria persistente del agente | accepted | tooling/ai | 2026-05-07 |
| [[0012-two-parallel-pocs]] | Dos PoCs paralelos (bare-javac vs Vert.x) | accepted | architecture/poc | 2026-05-07 |
| [[0013-layer-as-pod]] | Layer-as-Pod — cada capa en JVM separada | accepted | architecture/distributed | 2026-05-07 |
| [[0014-idempotency-keys-client-supplied]] | Las idempotency keys las provee el cliente | accepted | events/reliability | 2026-05-07 |
| [[0015-event-versioning-field]] | Event versioning vía campo eventVersion | accepted | events/schema | 2026-05-07 |
| [[0016-circuit-breaker-custom]] | Circuit breaker custom (sin Resilience4j) | accepted | resilience/poc | 2026-05-07 |
| [[0017-bare-javac-didactic-poc]] | Bare-javac para PoC didáctico (sin build tool en día 0) | accepted | tooling | 2026-05-07 |
| [[0018-maven-before-gradle]] | Gradle en PoCs antes de Gradle (cronológico) | accepted | tooling/build | 2026-05-07 |
| [[0019-gradle-kotlin-dsl]] | Gradle 8 con Kotlin DSL y version catalog | accepted | build/tooling | 2026-05-07 |
| [[0020-pkg-shared-modules]] | Módulos compartidos pkg/* como reactor Gradle | accepted | architecture/build | 2026-05-07 |
| [[0021-testcontainers-integration]] | Testcontainers para integration tests | accepted | testing/infrastructure | 2026-05-07 |
| [[0022-reporting-dual-layer]] | Reporting dual — resumen consola + archivo | accepted | tooling/observability | 2026-05-07 |
| [[0023-smoke-runner-asymmetric]] | Smoke runner asimétrico (suite completa vs subset) | accepted | testing/poc | 2026-05-07 |
| [[0024-ai-directory]] | Directorio .ai/ con primitivas de IA agnósticas de IDE | accepted | tooling/ai | 2026-05-07 |
| [[0025-skill-router-hybrid-scoring]] | Skill router con scoring híbrido stdlib | accepted | tooling/ai | 2026-05-07 |
| [[0026-convention-plugins]] | Convention plugins en build-logic/ | accepted | build/tooling | 2026-05-07 |
| [[0027-orbstack-k3d-autodetect]] | OrbStack k8s built-in vs autodetección de k3d | accepted | infrastructure/local-dev | 2026-05-07 |
| [[0028-minio-agpl-acceptable]] | MinIO AGPL-3.0 aceptable para PoC | superseded by 0042 | infrastructure/licensing | 2026-05-07 |
| [[0029-openbao-vs-vault]] | OpenBao (fork de Vault de Linux Foundation) | superseded by 0042 | infrastructure/security | 2026-05-07 |
| [[0030-redpanda-vs-kafka]] | Redpanda v24 en lugar de Kafka 3.9 para broker local | accepted | infrastructure/messaging | 2026-05-07 |
| [[0031-no-di-framework]] | Sin framework de DI — wiring manual en config/ | accepted | architecture/poc | 2026-05-07 |
| [[0032-jacoco-tcp-attach]] | Attach del JaCoCo TCP server para cobertura cross-module | accepted | testing/tooling | 2026-05-07 |
| [[0033-moto-inline-vs-localstack]] | Moto server para integration tests AWS | superseded by 0042 | testing/aws | 2026-05-07 |
| [[0034-doc-driven-vault-structure]] | Repo doc-driven — capas vault/, docs/, .ai/ | accepted | documentation/architecture | 2026-05-07 |
| [[0035-java-go-polyglot]] | Dos lenguajes — Java para apps, Go para CLI | accepted | architecture/tooling | 2026-05-07 |
| [[0036-archunit-structural-verification]] | ArchUnit para verificación estructural de la arquitectura | accepted | testing/architecture | 2026-05-07 |
| [[0037-virtual-threads-http-server]] | Virtual threads como executor del servidor HTTP | accepted | runtime/java/concurrency | 2026-05-07 |
| [[0038-riskplatform-package-namespace]] | Mantener `io.riskplatform.poc.*` como identificador técnico legacy | accepted | architecture/packaging | 2026-05-07 |
| [[0039-vertx-eventbus-host-advertisement]] | Configurar `EventBusOptions.setHost()` en cada Main del PoC distribuido | accepted | distributed/networking | 2026-05-07 |
| [[0042-floci-unified-aws-emulator]] | Floci como emulador AWS unificado (supersede 0005/0028/0029/0033) | accepted | infrastructure/aws/testing | 2026-05-11 |
| [[0043-kafka-broker-alternatives-eval]] | Tansu reemplaza a Redpanda como broker principal (compose + k8s + tests) | accepted | infrastructure/kafka/footprint | 2026-05-11 |

---

## Por área

### Arquitectura
- [[0002-enterprise-go-layout-in-java]] — layout de packages, Clean Architecture
- [[0012-two-parallel-pocs]] — dos PoCs con scopes distintos
- [[0013-layer-as-pod]] — separación física de capas
- [[0020-pkg-shared-modules]] — módulos de librería compartidos
- [[0031-no-di-framework]] — wiring manual de dependencias
- [[0034-doc-driven-vault-structure]] — estructura de documentación
- [[0035-java-go-polyglot]] — selección de lenguaje por dominio

### Sistemas distribuidos
- [[0003-vertx-for-distributed-poc]] — Vert.x + Hazelcast
- [[0013-layer-as-pod]] — layer-as-pod
- [[0030-redpanda-vs-kafka]] — broker compatible con Kafka
- [[0039-vertx-eventbus-host-advertisement]] — host advertisement del EventBus en Docker

### Eventos y confiabilidad
- [[0008-outbox-pattern-explicit]] — outbox transaccional
- [[0014-idempotency-keys-client-supplied]] — idempotencia
- [[0015-event-versioning-field]] — evolución de schema

### Plataforma Java
- [[0001-java-25-lts]] — versión del JDK
- [[0016-circuit-breaker-custom]] — circuit breaker
- [[0037-virtual-threads-http-server]] — virtual threads

### Build y tooling
- [[0017-bare-javac-didactic-poc]] — bare-javac
- [[0018-maven-before-gradle]] — cronología Gradle
- [[0019-gradle-kotlin-dsl]] — Gradle 8 + Kotlin DSL
- [[0026-convention-plugins]] — plugins de build-logic/

### Testing
- [[0006-atdd-karate-cucumber]] — frameworks ATDD
- [[0021-testcontainers-integration]] — estrategia de integration tests
- [[0032-jacoco-tcp-attach]] — cobertura cross-module
- [[0033-moto-inline-vs-localstack]] — integration tests AWS
- [[0036-archunit-structural-verification]] — reglas arquitecturales

### Infraestructura local
- [[0004-openobserve-otel]] — backend OTEL
- [[0005-aws-mocks-stack]] — mocks AWS (superseded by 0042)
- [[0007-k3d-orbstack-switch]] — k3d + OrbStack
- [[0027-orbstack-k3d-autodetect]] — selección del backend k8s
- [[0028-minio-agpl-acceptable]] — licencia de MinIO (superseded by 0042)
- [[0029-openbao-vs-vault]] — gestión de secrets (superseded by 0042)
- [[0042-floci-unified-aws-emulator]] — emulador AWS unificado (vigente)

### Tooling de IA
- [[0010-ide-agnostic-primitives]] — directorio .ai/
- [[0011-engram-mcp-memory]] — memoria persistente
- [[0024-ai-directory]] — racional de .ai/
- [[0025-skill-router-hybrid-scoring]] — routing de skills

---

## Top 5 para design review

Las cinco decisiones con más probabilidad de ser discutidas en un design review a nivel technical leadership, ranqueadas por densidad de señal de diseño:

1. **[[0013-layer-as-pod]]** — Layer-as-Pod con overhead medido (~19ms). Demuestra análisis de trade-offs de sistemas distribuidos con números reales.
2. **[[0008-outbox-pattern-explicit]]** — Identificar y arreglar un dual-write gap como mejora arquitectural al PoC. Demuestra conciencia de dominio y patrones de confiabilidad.
3. **[[0015-event-versioning-field]]** — Elegir un approach pragmático de versionado con un path documentado de migración a schema registry. Demuestra saber cuándo NO agregar infraestructura.
4. **[[0002-enterprise-go-layout-in-java]]** — Traducir el layout de clean architecture de Go a Java. Demuestra reconocimiento de patrones arquitecturales cross-language.
5. **[[0037-virtual-threads-http-server]]** — Virtual threads como executor HTTP con throughput medido (1528 req/s). Demuestra adopción práctica de Java moderno sobre baseline 21.
