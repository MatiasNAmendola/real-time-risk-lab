@outbox
Feature: Outbox pattern — event publishing

  Acceptance criteria:
  Después de evaluar una transacción, el evento de decisión debe aparecer
  en el outbox como PENDING y el OutboxRelay debe publicarlo dentro de 500ms.

  Scenario: Decision event is pending in outbox immediately after evaluation
    Given a customer "c-3" with stable history
    And a transaction "tx-outbox-1" of 2000 cents
    When the risk engine evaluates the transaction
    Then the outbox contains at least one pending event for transaction "tx-outbox-1"

  Scenario: OutboxRelay publishes pending events within 500ms
    Given a customer "c-3" with stable history
    And a transaction "tx-outbox-2" of 2000 cents
    When the risk engine evaluates the transaction
    And the outbox relay flushes
    Then the event for transaction "tx-outbox-2" is marked as published within 500 milliseconds
