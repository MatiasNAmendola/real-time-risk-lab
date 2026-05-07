---
name: testing-atdd
applies_to: ["**/src/test/**/*.java", "**/src/test/**/*.feature", "**/pom.xml"]
priority: high
---

# Regla: testing-atdd

## Principio

ATDD primero para features de integracion. TDD para internals de dominio.

## Orden de escritura

1. Escribir el `.feature` (Gherkin) que describe el comportamiento observable.
2. Correr: el test falla (RED).
3. Implementar lo minimo para que pase.
4. Refactorizar.
5. Correr de nuevo: verde.

## Frameworks por contexto

| Contexto | Framework | Version | Ubicacion |
|---|---|---|---|
| ATDD sobre Vert.x HTTP | Karate | 1.5+ | `poc/java-vertx-distributed/atdd-tests/` |
| ATDD bare-javac | Cucumber-JVM | 7+ | `tests/risk-engine-atdd/` |
| Unit tests | JUnit 5 | 5.11+ | modulo correspondiente |
| Coverage | JaCoCo | 0.8.12+ | todos los modulos Maven |

## Cobertura minima

- Line coverage: >= 80% en `domain/` y `application/`.
- Branch coverage: >= 75%.
- `infrastructure/` puede tener menor cobertura si hay integraciones dificiles de testear.

## No permitido

- Tests que duermen (`Thread.sleep`) para esperar resultados asincronos. Usar `CountDownLatch`, `VertxTestContext`, o `awaitility`.
- `@Disabled` sin comentario explicando por que y con issue asociado.
- Tests que modifican estado global sin restaurarlo en `@AfterEach`.
- Feature files sin escenario de unhappy path (error handling).

## Estructura de feature file

```gherkin
Feature: <nombre del comportamiento>
  Como <rol>
  Quiero <accion>
  Para <beneficio>

  Background:
    Given <precondicion comun>

  Scenario: happy path
    ...

  Scenario: error case - <tipo de error>
    ...
```

## Verificacion

```bash
mvn test -pl atdd-tests          # Karate
mvn test -pl tests/risk-engine-atdd  # Cucumber
mvn verify -pl <module>          # JaCoCo check
```
