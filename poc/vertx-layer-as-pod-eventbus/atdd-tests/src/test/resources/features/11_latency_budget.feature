@latency @rest
Feature: Latency budget enforcement

  # EvaluateRiskVerticle starts a LatencyBudget(280ms) per request.
  # The following scenarios verify observable behaviour derived from that budget:
  #
  #   1. Responses must arrive well within the HTTP SLA (< 300 ms under no-load conditions).
  #   2. The decision is always returned even under budget pressure (degraded but not stuck).
  #   3. The decision shape is consistent regardless of whether the ML scorer ran.
  #
  # Note: these tests run against a live stack (compose up required).  They are tagged @rest
  # so they are included in the standard RestRunner.

  Background:
    * url baseUrl
    * configure connectTimeout = 2000
    * configure readTimeout = 5000

  Scenario: Single evaluation completes within 300 ms under no-load conditions
    * def start = java.lang.System.currentTimeMillis()
    Given path 'risk'
    And request { transactionId: 'budget-tx-1', customerId: 'c-2', amountCents: 1000 }
    When method POST
    Then status 200
    * def elapsed = java.lang.System.currentTimeMillis() - start
    # Under no-load with local Docker, 300 ms is a generous but observable bound.
    * assert elapsed < 300
    And match response.decision == '#string'

  Scenario: Decision is always returned even when customerId triggers heavier processing
    # c-4 (riskScore=0.95) exercises the ML score path.  Response must still arrive promptly.
    * def start = java.lang.System.currentTimeMillis()
    Given path 'risk'
    And request { transactionId: 'budget-tx-2', customerId: 'c-4', amountCents: 500 }
    When method POST
    Then status 200
    * def elapsed = java.lang.System.currentTimeMillis() - start
    * assert elapsed < 300
    And match response.decision == 'DECLINE'

  Scenario: Budget-sensitive new-device request completes within SLA
    # NewDeviceYoungCustomerRule (c-3, age=20 days) adds rule evaluation overhead.
    * def start = java.lang.System.currentTimeMillis()
    Given path 'risk'
    And request { transactionId: 'budget-tx-3', customerId: 'c-3', amountCents: 500, newDevice: true }
    When method POST
    Then status 200
    * def elapsed = java.lang.System.currentTimeMillis() - start
    * assert elapsed < 300
    And match response.decision == 'REVIEW'
