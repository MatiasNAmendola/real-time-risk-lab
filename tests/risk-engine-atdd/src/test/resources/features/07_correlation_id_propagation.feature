@correlation-id
Feature: Correlation ID propagation

  Acceptance criteria:
  El correlationId provisto en el request debe propagarse a través de la traza
  y aparecer también en el evento publicado al outbox.

  Scenario: Correlation ID appears in DecisionTrace
    Given a customer "c-4" with stable history
    And a transaction "tx-corr-1" of 1500 cents
    And the request carries correlation id "corr-xyz-001"
    When the risk engine evaluates the transaction
    Then the decision trace contains correlation id "corr-xyz-001"

  Scenario: Correlation ID appears in the outbox event
    Given a customer "c-4" with stable history
    And a transaction "tx-corr-2" of 1500 cents
    And the request carries correlation id "corr-xyz-002"
    When the risk engine evaluates the transaction
    Then the outbox event for the transaction has correlation id "corr-xyz-002"
