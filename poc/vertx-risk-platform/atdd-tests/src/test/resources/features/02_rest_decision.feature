@rest @decision
Feature: REST sync decision via controller-pod

  # Tests the synchronous POST /risk/evaluate endpoint via controller-pod (port 8180).
  # The controller calls usecase (8181) which calls repository (8182) via HTTP + token auth.
  # This is the primary flow that differentiates vertx-risk-platform from vertx-distributed
  # (which uses event bus instead of HTTP between pods).

  Background:
    * url 'http://localhost:8180'
    * configure connectTimeout = 2000
    * configure readTimeout = 8000

  Scenario Outline: Evaluate transaction <transactionId> with amount <amountInCents> cents
    Given path '/risk/evaluate'
    And request { transactionId: '<transactionId>', customerId: 'c-1', amountInCents: <amountInCents>, newDevice: false, correlationId: 'corr-atdd-1', idempotencyKey: '<transactionId>-key' }
    When method POST
    Then status 200
    And match response.decision == '<expected>'
    And match response.transactionId == '<transactionId>'
    And match response.trace.correlationId == 'corr-atdd-1'

    Examples:
      | transactionId      | amountInCents | expected |
      | tx-atdd-low-1      | 1000          | APPROVE  |
      | tx-atdd-review-1   | 50000         | REVIEW   |

  Scenario: High amount returns REVIEW (amount-over-threshold rule)
    Given path '/risk/evaluate'
    And request { transactionId: 'tx-atdd-high-1', customerId: 'c-1', amountInCents: 500000, newDevice: false, correlationId: 'corr-atdd-2', idempotencyKey: 'ik-atdd-high-1' }
    When method POST
    Then status 200
    And match response.decision == 'REVIEW'
    And match response.reason == 'amount-over-threshold'

  Scenario: Decision response includes trace with ruleSetVersion and evaluatedRules
    Given path '/risk/evaluate'
    And request { transactionId: 'tx-atdd-trace-1', customerId: 'c-1', amountInCents: 2000, newDevice: false, correlationId: 'corr-atdd-3', idempotencyKey: 'ik-atdd-trace-1' }
    When method POST
    Then status 200
    And match response.trace.ruleSetVersion == '#string'
    And match response.trace.evaluatedRules == '#array'
    And match response.elapsedMs == '#number'
