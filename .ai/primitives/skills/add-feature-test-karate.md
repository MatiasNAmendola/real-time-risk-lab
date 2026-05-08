---
name: add-feature-test-karate
intent: Agregar un feature test con Karate para ATDD sobre un endpoint Vert.x
inputs: [feature_name, endpoint, scenarios]
preconditions:
  - poc/java-vertx-distributed/atdd-tests/ o poc/vertx-risk-platform existe
  - Servidor Vert.x arrancable via ./gradlew exec:java o main class
postconditions:
  - Archivo .feature en src/test/resources/features/
  - Step definitions si se usan pasos custom
  - ./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd pasa en verde
related_rules: [testing-atdd, java-version, communication-patterns]
---

# Skill: add-feature-test-karate

## Pasos

1. **Crear feature file** en `atdd-tests/src/test/resources/features/<feature>.feature`:
   ```gherkin
   Feature: Risk Engine REST API

   Background:
     * url 'http://localhost:8080'
     * header Content-Type = 'application/json'

   Scenario: evaluar transaccion de alto riesgo
     Given path '/risk'
     And request { "transactionId": "tx-001", "amountARS": 500000, "merchantId": "m-001" }
     When method POST
     Then status 200
     And match response.decision == 'DECLINE'
     And match response.correlationId != null
   ```

2. **Runner** (si no existe) `src/test/java/.../KarateRunner.java`:
   ```java
   @RunWith(Karate.class)
   public class KarateRunner {
       // Karate autodescubre features en resources/features/
   }
   ```
   O con JUnit 5:
   ```java
   class KarateTest {
       @Test
       void testAll() {
           var results = Karate.run("features").relativeTo(getClass());
           assertTrue(results.getFailCount() == 0, results.getErrorMessages());
       }
   }
   ```

3. **Arrancar servidor en tests**: usar `@BeforeAll` para levantar el Verticle en modo test:
   ```java
   @BeforeAll
   static void startServer() {
       // Levantar verticle en puerto de test
   }
   ```

4. **Ejecutar**: `./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd -Dtest=KarateRunner`.

## Notas
- Karate no requiere step definitions para HTTP basico. Solo el `.feature`.
- Usar `karate.callSingle()` para setup compartido entre features.
- Para SSE y WS, Karate tiene soporte nativo desde 1.3+.
- Version: Karate 1.5+.
