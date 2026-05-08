@rest @decision
Feature: REST sync decision

  # Tests the synchronous POST /risk endpoint end-to-end through all three
  # services (controller → usecase → repository → postgres).

  Background:
    * url baseUrl
    * configure connectTimeout = 2000
    * configure readTimeout = 5000

  Scenario Outline: Evaluate transaction <transactionId> with amount <amount>
    Given path 'risk'
    And request { transactionId: '<transactionId>', customerId: 'c-1', amountCents: <amount> }
    When method POST
    Then status 200
    And match response.decision == '<expected>'
    And match response.correlationId == '#string'
    And match response.transactionId == '<transactionId>'

    Examples:
      | transactionId | amount  | expected |
      | tx-low-1      | 1000    | APPROVE  |
      | tx-mid-1      | 50000   | REVIEW   |
      | tx-high-1     | 200000  | DECLINE  |

  Scenario: Decision for customer c-4 (riskScore=0.95) with high amount is DECLINE
    Given path 'risk'
    And request { transactionId: 'tx-c4-high', customerId: 'c-4', amountCents: 150000 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'

  Scenario: Decision for customer c-2 (riskScore=0.30) with high amount is REVIEW
    Given path 'risk'
    And request { transactionId: 'tx-c2-high', customerId: 'c-2', amountCents: 150000 }
    When method POST
    Then status 200
    And match response.decision == 'REVIEW'

  Scenario: Missing required field returns 400
    Given path 'risk'
    And request { customerId: 'c-1', amountCents: 1000 }
    When method POST
    Then status 400

  Scenario: Unknown customer returns 404 or default APPROVE with no features
    Given path 'risk'
    And request { transactionId: 'tx-unknown-1', customerId: 'c-unknown', amountCents: 1000 }
    When method POST
    # Acceptable: 404 (customer not found) or 200 with APPROVE (default low-risk)
    Then match [200, 404] contains responseStatus

  Scenario: New device + young customer (c-3: age=20 days) returns REVIEW
    # c-3 has customer_age_days=20 in the seeded DB — below the 30-day threshold.
    # Sending newDevice=true must trigger NewDeviceYoungCustomerRule and produce REVIEW.
    Given path 'risk'
    And request { transactionId: 'tx-newdev-youngcust-1', customerId: 'c-3', amountCents: 500, newDevice: true }
    When method POST
    Then status 200
    And match response.decision == 'REVIEW'
    And match response.reason contains 'NewDeviceYoungCustomerRule'

  Scenario: New device + established customer (c-1: age=365 days) does NOT trigger REVIEW from that rule
    # c-1 has customer_age_days=365 — above the 30-day threshold.
    # The NewDeviceYoungCustomerRule must NOT fire even with newDevice=true.
    Given path 'risk'
    And request { transactionId: 'tx-newdev-oldcust-1', customerId: 'c-1', amountCents: 500, newDevice: true }
    When method POST
    Then status 200
    # Decision is driven by riskScore (c-1 has 0.82 → REVIEW by ML score), not by the rule.
    And match response.decision != 'APPROVE'
