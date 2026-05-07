@new-device
Feature: Flag new-device transactions for young customers

  Acceptance criteria:
  Clientes con cuenta joven (menos de 30 días) que usan un dispositivo nuevo
  deben activar la regla new-device-young-customer-v1 y resultar en REVIEW.

  Scenario: New device and customer age under 30 days triggers review
    Given a customer "young-c-1" with stable history
    And the customer account is 10 days old
    And a transaction "tx-nd-1" of 2000 cents from a new device
    When the risk engine evaluates the transaction
    Then the decision is "REVIEW"

  # Old customer, known device, small amount: rule does not fire, no risk signals => APPROVE
  Scenario: Known device and older customer is not flagged
    Given a customer "old-c-1" with stable history
    And the customer account is 90 days old
    And a transaction "tx-nd-2" of 2000 cents
    When the risk engine evaluates the transaction
    Then the decision is "APPROVE"
