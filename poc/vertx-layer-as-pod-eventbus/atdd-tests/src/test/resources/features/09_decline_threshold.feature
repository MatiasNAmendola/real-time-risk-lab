@decline @threshold
Feature: DECLINE threshold boundary conditions

  # Validates the exact boundary conditions from the policy:
  #   amountCents > 100_000  AND  riskScore > 0.7  →  DECLINE
  #   amountCents > 50_000                          →  REVIEW
  #   otherwise                                     →  APPROVE
  #
  # Customer c-1 has riskScore=0.82 (above 0.7)
  # Customer c-2 has riskScore=0.30 (below 0.7)
  # Customer c-5 has riskScore=0.10 (below 0.7)

  Background:
    * url baseUrl
    * configure readTimeout = 5000

  Scenario Outline: Boundary condition — customer <customerId>, amount <amount> → <expected>
    Given path 'risk'
    And request { transactionId: 'boundary-<customerId>-<amount>', customerId: '<customerId>', amountCents: <amount> }
    When method POST
    Then status 200
    And match response.decision == '<expected>'

    Examples:
      # APPROVE: amount <= 50000
      | customerId | amount | expected |
      | c-1        | 1      | APPROVE  |
      | c-1        | 49999  | APPROVE  |
      | c-1        | 50000  | APPROVE  |
      # REVIEW: amount > 50000 but NOT (amount > 100000 AND riskScore > 0.7)
      | c-1        | 50001  | REVIEW   |
      | c-1        | 100000 | REVIEW   |
      | c-2        | 150000 | REVIEW   |
      # DECLINE: amount > 100000 AND riskScore > 0.7
      | c-1        | 100001 | DECLINE  |
      | c-1        | 200000 | DECLINE  |
      | c-4        | 100001 | DECLINE  |

  Scenario: Exact boundary at 100_001 cents for c-1 is DECLINE
    Given path 'risk'
    And request { transactionId: 'boundary-exact-100001', customerId: 'c-1', amountCents: 100001 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'

  Scenario: Exact boundary at 100_000 cents for c-1 is REVIEW (not DECLINE — strict gt)
    Given path 'risk'
    And request { transactionId: 'boundary-exact-100000', customerId: 'c-1', amountCents: 100000 }
    When method POST
    Then status 200
    And match response.decision == 'REVIEW'
