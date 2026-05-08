# Decisions Log (ADR-style)

## ADR-001: Java 21 baseline operativo + Java 25 objetivo LTS

**Status**: accepted
**Date**: 2026-01-01
**Deciders**: Matias Amendola

### Contexto
Se necesita elegir una version de Java para todas las PoCs. El objetivo es demostrar dominio del lenguaje moderno y ser defensible como production target.

### Decision
Baseline actual Java 21 LTS con `--release 21`; Java 25 LTS queda como objetivo documentado cuando el tooling lo soporte.

### Consecuencias positivas
- Virtual threads (Loom) disponibles para I/O bloqueante.
- Records y sealed classes disponibles.
- Version LTS: soporte a largo plazo, usable en produccion.
- Java 25 queda como objetivo LTS futuro; el build verificable actual se mantiene en Java 21.

### Consecuencias negativas / riesgos
- Java 26 existe pero no es LTS. Java 25 es el objetivo LTS documentado, no el build actual.
- Algunas librerias mayores (como ciertas versiones de Hazelcast) pueden tener advertencias con Java 25+.

---

## ADR-002: Replicar layout enterprise Go en Java

**Status**: accepted
**Date**: 2026-01-01

### Contexto
El uso de una estructura de directorios canonica en Go inspirado por equipos polyglot. Las PoCs Java replican ese layout para demostrar Clean Arch sin Spring.

### Decision
`domain/{entity,repository,usecase,service,rule}`, `application/usecase/<aggregate>/`, `application/mapper/`, `infrastructure/{controller,consumer,repository,resilience,time}`, `cmd/`, `config/`.

### Consecuencias positivas
- Demuestra conocimiento del layout canonico enterprise Go.
- Facilita navegacion para reviewers.
- Fuerza separacion de concerns por diseño.

### Consecuencias negativas / riesgos
- Mas directorios que un layout Spring Boot tipico. Puede parecer excesivo para una PoC.
- Compensacion: un reviewer tecnico valora el dominio de best practices del ecosistema.

---

## ADR-003: Vert.x 5 para PoC distribuida

**Status**: accepted
**Date**: 2026-01-15

### Contexto
Se necesita un framework para la capa HTTP reactiva. Spring Boot WebFlux o Vert.x son las opciones.

### Decision
Vert.x 5.0.12.

### Consecuencias positivas
- Event loop model alineado con el modelo reactivo del use case.
- Excelente performance: benchmark muestra ~150K req/s en hardware modesto.
- Vert.x 5 corre bien sobre Java moderno; el repo lo compila con bytecode Java 21 para evitar fricción de tooling.
- Menor overhead que Spring Boot (no application context pesado).

### Consecuencias negativas / riesgos
- Menor adopcion que Spring Boot: mas dificil de encontrar ejemplos.
- Programacion reactiva con Futures/Promise tiene curva de aprendizaje.

---

## ADR-004: OpenObserve como backend OTEL

**Status**: accepted
**Date**: 2026-01-15

### Contexto
Se necesita un backend para traces, logs y metricas OTEL. Opciones: Jaeger, Tempo+Grafana, Axiom, OpenObserve.

### Decision
OpenObserve standalone.

### Consecuencias positivas
- Un solo binario para traces + logs + metricas (no necesita Jaeger + Loki + Grafana separados).
- API OTLP compatible.
- UI decente para busqueda de traces y logs.
- Facil de correr en docker-compose y k8s.

### Consecuencias negativas / riesgos
- Menos documentacion que Jaeger/Grafana stack.
- Mas joven como proyecto.

---

## ADR-005: Stack AWS-mocks (Moto + MinIO + ElasticMQ + OpenBao)

**Status**: accepted
**Date**: 2026-02-01

### Contexto
Las demos deben replicar el uso de servicios AWS (S3, SQS, Secrets Manager) sin cuenta real.

### Decision
Moto (multi-servicio), MinIO (S3), ElasticMQ (SQS), OpenBao (Secrets Manager).

### Consecuencias positivas
- Mismo codigo Java funciona contra AWS real y contra mocks.
- No requiere cuenta AWS para desarrollo local.
- Demostra conocimiento de como funciona la infra de produccion.

### Consecuencias negativas / riesgos
- MinIO es AGPL: solo para dev/test (ver rule licensing).
- OpenBao es MPL: libre para usar como servicio.

---

## ADR-006: ATDD con Karate y Cucumber-JVM

**Status**: accepted
**Date**: 2026-02-01

### Contexto
Se necesita una estrategia de testing que demuestre ATDD y sea demostrable en review tecnica.

### Decision
- Karate 1.5+ para ATDD sobre Vert.x (dentro de los PoCs Gradle).
- Cucumber-JVM 7 para ATDD en `tests/risk-engine-atdd/` (independiente de frameworks).

### Consecuencias positivas
- Karate no requiere step definitions para HTTP basico: escribir solo el .feature.
- Cucumber-JVM demuestra conocimiento de BDD clasico con step definitions.
- Ambos producen reportes HTML legibles para mostrar en review.

### Consecuencias negativas / riesgos
- Mantener dos frameworks de ATDD tiene overhead.
- Compensacion: cada uno demuestra una habilidad diferente.

---

## ADR-007: k3d con switch a OrbStack

**Status**: accepted
**Date**: 2026-02-15

### Contexto
Se necesita un provider de k8s local que funcione en Mac y sea rapido para demos.

### Decision
Autodetect: si `orb` esta en PATH → OrbStack. Sino → k3d.

### Consecuencias positivas
- OrbStack: arranque < 10s, LoadBalancer real.
- k3d: cross-platform, cluster aislado.
- Mismo script `up.sh` funciona en ambos.

### Consecuencias negativas / riesgos
- OrbStack es Mac-only y de pago.
- k3d tiene LoadBalancer simulado (port-forward).

---

## ADR-008: Sistema .ai/ de primitivas IDE-agnosticas

**Status**: accepted
**Date**: 2026-05-07

### Contexto
Multiples agentes IA necesitan trabajar sobre este repo sin tener que leer todo el codebase para entender el contexto.

### Decision
Sistema `.ai/` con primitivas (skills, rules, workflows, hooks) y adapters por IDE.

### Consecuencias positivas
- Cualquier agente puede operar el repo leyendo solo `.ai/` + AGENTS.md.
- Los adapters traducen las primitivas al formato nativo de cada IDE.
- Sin duplicacion: adapters referencian primitivas, no las copian.

### Consecuencias negativas / riesgos
- Overhead de mantener los archivos actualizados.
- Compensacion: el verify-primitives.sh detecta archivos faltantes o mal formados.

---

## ADR-009: Redpanda en lugar de Apache Kafka

**Status**: accepted
**Date**: 2026-01-15

### Contexto
Se necesita un broker Kafka-compatible para demos locales.

### Decision
Redpanda v24.2.4.

### Consecuencias positivas
- API 100% compatible con Kafka. El codigo Java usa el mismo cliente.
- Single binary: mucho mas simple de operar en local que Kafka + ZooKeeper.
- Arranque rapido en docker.

### Consecuencias negativas / riesgos
- No es Apache Kafka. En produccion real probablemente se use Kafka o MSK.
- Compensacion: la API es identica, la diferencia es operativa.

---

## ADR-010: Valkey en lugar de Redis

**Status**: accepted
**Date**: 2026-01-15

### Contexto
Se necesita un cache en memoria con API Redis-compatible.

### Decision
Valkey 8-alpine.

### Consecuencias positivas
- Fork open source de Redis (BSD 3-Clause).
- API 100% compatible con Redis. El codigo Java usa el mismo cliente (Vert.x Redis client).
- Sin cambios de licencia como los de Redis Ltd.

### Consecuencias negativas / riesgos
- Menos documentacion y adopcion que Redis.
- En produccion podrian usar ElastiCache (Redis-compatible): mismo cliente Java.

---

## ADR-011: No usar Spring Boot en las PoCs

**Status**: accepted
**Date**: 2026-01-01

### Contexto
Spring Boot es el framework mas comun en Java. Usar Vert.x o bare-javac puede parecer inusual.

### Decision
Sin Spring Boot en las PoCs. Vert.x 5 para reactive, bare-javac para demos de arquitectura pura.

### Consecuencias positivas
- Demuestra que entendemos los conceptos sin depender del framework.
- Performance superior al no tener el overhead de Spring container.
- Codigo mas explicito: cada dependencia es visible.

### Consecuencias negativas / riesgos
- Sin auto-configuration: wiring manual en `config/`.
- Compensacion: un reviewer puede ver exactamente como funciona el wiring.
