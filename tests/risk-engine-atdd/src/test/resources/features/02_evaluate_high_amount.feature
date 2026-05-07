@high-amount
Feature: Decline or review high-amount transactions

  Acceptance criteria:
  Transacciones que superen el umbral de monto alto
  deben ser declinadas o enviadas a revisión por la regla HighAmountRule.

  Scenario: Transaction above high-amount threshold triggers rule
    Given a customer "c-2" with stable history
    And a transaction "tx-high-1" of 75000 cents
    When the risk engine evaluates the transaction
    Then the decision is not "APPROVE"

  Scenario: Transaction just below threshold is approved
    Given a customer "c-2" with stable history
    And a transaction "tx-low-1" of 5000 cents
    When the risk engine evaluates the transaction
    Then the decision is "APPROVE"
