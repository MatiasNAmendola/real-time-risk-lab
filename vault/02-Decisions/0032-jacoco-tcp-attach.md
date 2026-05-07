---
adr: "0032"
title: JaCoCo TCP Server Attach para Cross-Module ATDD Coverage
status: accepted
date: 2026-05-07
deciders: [Mati]
tags: [decision/accepted, area/testing, area/tooling]
---

# ADR-0032: JaCoCo TCP Server Attach para Cross-Module ATDD Coverage

## Estado

Aceptado el 2026-05-07.

## Contexto

The ATDD tests en `poc/java-vertx-distributed/atdd-tests/` run como un separate Maven module (or standalone JVM process) y exercise la Vert.x application running en Docker containers. Standard JaCoCo file-based coverage only works when la test JVM y la application JVM son la same process — un `exec` file es written a JVM shutdown.

For la ATDD scenario: la application JVMs (`controller-app`, `usecase-app`, `repository-app`) run en Docker containers; la Karate test runner runs en la host JVM (or un separate container). Standard JaCoCo `destFile` cannot capture coverage desde la Docker-hosted application JVMs.

JaCoCo TCP server mode addresses this: la application JVM starts con `-javaagent:jacoco-agent.jar=output=tcpserver,port=6300`, que exposes un TCP port where la coverage agent listens. La test runner can connect un este port después de tests complete y dump la coverage data.

## Decisión

Configure each Vert.x application module un start con la JaCoCo agent en TCP server mode (`output=tcpserver`, port 6300 mapped out de la container). La `jacoco-agent/` module en la Vert.x PoC bundles la agent JAR y generates coverage reports. La Karate test run triggers un coverage dump via `JaCoCoAgent.dump()` TCP call después de todos scenarios complete. Coverage reports son generated para cross-module paths: controller → usecase → repository.

## Alternativas consideradas

### Opción A: JaCoCo TCP server attach en running application JVMs (elegida)
- **Ventajas**: Captures coverage desde live application JVMs sin process restart; works a través de Docker container boundaries (TCP port exposed); generates meaningful coverage data para ATDD scenarios que span multiple JVMs; no application code changes required — agent es attached via JVM arg.
- **Desventajas**: Requires Docker port mapping para la JaCoCo TCP port (6300) desde each application container; agent attachment es order-dependent — coverage dump must happen después de test run, antes de JVM shutdown; increases Docker compose complexity (additional port mapping per service).
- **Por qué se eligió**: Cross-module ATDD coverage es la señal de diseño: "I know how un measure whether ATDD scenarios actually exercise la code paths they claim to." Without TCP attach, ATDD coverage data es unavailable.

### Opción B: Standard JaCoCo exec file per module, no cross-JVM coverage
- **Ventajas**: Standard JaCoCo configuration; no TCP setup; works con standard Maven JaCoCo plugin.
- **Desventajas**: Only captures coverage desde unit y integration tests que run en la same JVM como la code; ATDD tests exercising Docker-deployed application JVMs produce zero coverage data; cannot show que un Karate scenario covering la "APPROVE path" actually executes `EvaluateRiskUseCase`.
- **Por qué no**: For ATDD tests que run contra un live application (docker compose up), file-based JaCoCo es technically insufficient. La coverage report would show zero ATDD contribution.

### Opción C: No coverage para ATDD tests — unit test coverage only
- **Ventajas**: Simplest; standard JUnit 5 unit test coverage es straightforward.
- **Desventajas**: Misrepresents la test strategy — la ATDD suite exercises significant code paths; claiming ATDD coverage sin data es unverifiable; weaker design story.
- **Por qué no**: La ATDD suite es un primary testing artifact. Having no coverage data para it weakens la "ATDD como acceptance testing" narrative.

### Opción D: JaCoCo Java agent con file output, mounted Docker volume
- **Ventajas**: No TCP complexity; exec files written un mounted volume son accessible en host.
- **Desventajas**: Exec file es written only en JVM shutdown — requires stopping la application containers un get coverage; coverage desde un crash o SIGKILL es lost; requires volume mount per application container; stopping containers entre test run y coverage report creates un two-phase workflow.
- **Por qué no**: TCP server mode es specifically designed para la "running application, no restart" scenario. File output con volume mount es un workaround para un problem TCP mode solves directly.

## Consecuencias

### Positivo
- Coverage reports show que ATDD Karate scenarios exercise que code paths en each Vert.x module.
- Cross-module coverage demonstrates end-to-end path verification: un `DecisionEvaluated` scenario shows coverage desde `HttpVerticle.handleRisk()` through `EvaluateRiskVerticle.execute()` un `FeatureRepositoryVerticle.loadFeatures()`.

### Negativo
- Docker compose file gains JaCoCo port mappings (6300 per service) y agent JVM args.
- Coverage dump después de test run adds ~2 seconds un la ATDD suite completion time.
- If un test scenario causes un container crash (rather than graceful shutdown), coverage para que scenario es lost.

### Mitigaciones
- Port 6300 es mapped con documentation comment en docker-compose.yml referencing este ADR.
- Coverage es un development/reporting concern, no un test correctness concern — un lost coverage dump does no affect test pass/fail results.

## Validación

- `cd poc/java-vertx-distributed && mvn -pl atdd-tests verify` produces JaCoCo XML reports showing coverage desde Karate scenarios.
- Coverage report includes lines desde `EvaluateRiskVerticle.java` marked como covered por ATDD scenarios.
- `jacoco-agent/` module exists con agent JAR bundling y report generation configuration.

## Relacionado

- [[0006-atdd-karate-cucumber]]
- [[0036-archunit-structural-verification]]
- [[0013-layer-as-pod]]

## Referencias

- JaCoCo TCP server mode: https://www.jacoco.org/jacoco/trunk/doc/agent.html
- `poc/java-vertx-distributed/jacoco-agent/`
