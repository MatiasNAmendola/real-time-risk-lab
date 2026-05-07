---
name: add-fraud-rule
intent: Agregar una regla deterministica al motor de decisiones de riesgo
inputs: [rule_name, rule_description, input_fields, decision_output, threshold_config]
preconditions:
  - domain/rule/ existe en el PoC objetivo
  - RuleEngine o equivalente tiene metodo para registrar reglas
postconditions:
  - Nueva clase FraudRule implementa la interfaz de regla
  - Regla configurada via config (no hardcoded)
  - Unit test cubre casos: true positive, false positive, edge case
  - ATDD scenario cubre la regla en flujo completo
related_rules: [architecture-clean, java-version, testing-atdd, naming-conventions]
---

# Skill: add-fraud-rule

## Pasos

1. **Interfaz de regla** (si no existe) en `domain/rule/FraudRule.java`:
   ```java
   public interface FraudRule {
       String name();
       RuleResult evaluate(TransactionContext ctx);
   }
   ```

2. **Implementar la regla** en `domain/rule/<RuleName>Rule.java`:
   ```java
   public class HighAmountRule implements FraudRule {
       private final long thresholdARS;

       public HighAmountRule(long thresholdARS) {
           this.thresholdARS = thresholdARS;
       }

       @Override
       public String name() { return "HIGH_AMOUNT"; }

       @Override
       public RuleResult evaluate(TransactionContext ctx) {
           if (ctx.amountARS() > thresholdARS) {
               return RuleResult.decline("Amount exceeds threshold " + thresholdARS);
           }
           return RuleResult.pass();
       }
   }
   ```

3. **Registrar en RuleEngine** (config o wiring):
   - Inyectar threshold desde config (`rules.high-amount.threshold-ars`).
   - Agregar a la lista de reglas evaluadas en orden.

4. **Unit test** `domain/rule/HighAmountRuleTest.java`:
   - Caso DECLINE: monto > threshold.
   - Caso PASS: monto <= threshold.
   - Caso edge: monto == threshold exacto.

5. **ATDD**:
   ```gherkin
   Scenario: regla de monto alto declina transaccion
     Given the high amount threshold is 100000 ARS
     When I evaluate a transaction with amount 150000 ARS
     Then the decision is DECLINE
     And the reason contains "HIGH_AMOUNT"
   ```

6. **Observabilidad**: log la regla que disparó el DECLINE con nivel INFO.

## Notas
- Las reglas son puras (no tienen side effects, no consultan infraestructura).
- Si la regla necesita datos externos (velocidad, historial), esos datos deben venir en el TransactionContext, no dentro de la regla.
- Ordenar reglas de menor costo a mayor costo computacional (fail fast).
