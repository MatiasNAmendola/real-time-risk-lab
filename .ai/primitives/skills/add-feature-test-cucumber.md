---
name: add-feature-test-cucumber
intent: Agregar un feature test con Cucumber-JVM para ATDD en tests/risk-engine-atdd/
inputs: [feature_name, scenarios, step_definitions_package]
preconditions:
  - tests/risk-engine-atdd/ existe
  - pom.xml tiene cucumber-java y cucumber-junit5 en scope test
postconditions:
  - Feature file en src/test/resources/features/
  - Step definitions en src/test/java/.../steps/
  - mvn test en tests/risk-engine-atdd pasa en verde
related_rules: [testing-atdd, java-version, naming-conventions]
---

# Skill: add-feature-test-cucumber

## Pasos

1. **Feature file** en `tests/risk-engine-atdd/src/test/resources/features/<feature>.feature`:
   ```gherkin
   Feature: Evaluacion de riesgo transaccional

   Scenario: transaccion normal aprobada
     Given el motor de riesgo esta corriendo
     When evaluo una transaccion con monto 1000 ARS y comercio "tienda-A"
     Then la decision es APPROVE
     And el correlationId esta presente en la respuesta

   Scenario Outline: umbrales de decision
     Given el motor de riesgo esta corriendo
     When evaluo una transaccion con monto <monto> ARS
     Then la decision es <decision>
     Examples:
       | monto  | decision |
       | 1000   | APPROVE  |
       | 500000 | DECLINE  |
   ```

2. **Step definitions** en `src/test/java/.../steps/RiskSteps.java`:
   ```java
   public class RiskSteps {
       private Response lastResponse;

       @Given("el motor de riesgo esta corriendo")
       public void motorCorriendo() {
           // Verificar /healthz 200
       }

       @When("evaluo una transaccion con monto {long} ARS y comercio {string}")
       public void evaluarTransaccion(long monto, String comercio) {
           // POST /risk
       }

       @Then("la decision es {string}")
       public void verificarDecision(String expected) {
           assertEquals(expected, lastResponse.getDecision());
       }
   }
   ```

3. **Runner JUnit 5**:
   ```java
   @Suite
   @IncludeEngines("cucumber")
   @SelectClasspathResource("features")
   @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.naranjax.steps")
   class CucumberRunner {}
   ```

4. **Ejecutar**: `mvn test -pl tests/risk-engine-atdd`.

## Notas
- Version: Cucumber-JVM 7+ con JUnit 5.
- Usar `World` o `ScenarioContext` para compartir estado entre steps sin variables estaticas.
- Step definitions deben ser thin: delegan a clientes HTTP, no tienen logica de negocio.
