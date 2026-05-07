@low-risk
Feature: Approve low-risk transactions

  Acceptance criteria:
  Como sistema antifraude
  cuando una transacción es de bajo monto y el cliente tiene historial estable
  el sistema debe aprobarla en el camino crítico sin tocar el modelo ML.

  # Core scenario: small amount, good history => APPROVE decision
  Scenario: Customer with good history, small amount
    Given a customer "c-1" with stable history
    And a transaction "tx-1" of 1000 cents
    When the risk engine evaluates the transaction
    Then the decision is "APPROVE"
    And the latency budget consumed less than 500 milliseconds

  # NOTE: The current PoC always calls the ML scorer when no rule fires.
  # The "ML not invoked" path would require a pre-score APPROVE fast-path rule
  # (e.g. a LowAmountFastApproveRule) which is not yet implemented in the PoC.
  # Marked @wip until the PoC supports it.
  @wip
  Scenario: ML scorer is bypassed for small known-safe transactions
    Given a customer "c-1" with stable history
    And a transaction "tx-1-nowip" of 1000 cents
    When the risk engine evaluates the transaction
    Then the decision is "APPROVE"
    And the ML scorer was not invoked
