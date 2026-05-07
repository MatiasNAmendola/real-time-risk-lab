@idempotency
Feature: Idempotent decisions on retries

  Acceptance criteria:
  Si el mismo idempotency key se envía más de una vez,
  el motor debe devolver exactamente la misma decisión sin re-evaluar las reglas.

  Scenario: Same idempotency key returns the same decision
    Given a customer "c-1" with stable history
    And a transaction "tx-1" of 1000 cents with idempotency key "key-abc"
    When the risk engine evaluates the transaction
    And the same request is sent again with the same idempotency key
    Then both responses have the same decision
    And the second evaluation did not invoke the rules engine
