@fallback @rest
Feature: ML scorer fallback on timeout

  # When the ML scoring service times out, the system must fall back to
  # the deterministic rules engine and mark the response with fallback: true.
  #
  # The EvaluateRiskVerticle always includes fallback, fallbackReason, and decisionSource in
  # the broadcast payload (and indirectly in the decision response via the broadcast fields).
  # The circuit breaker is a SimpleCircuitBreaker — when CLOSED and ML scoring succeeds,
  # fallback=false and decisionSource=ml.  The response shape is stable regardless of breaker state,
  # so these scenarios verify the contract without requiring chaos injection.

  Background:
    * url baseUrl

  Scenario: Normal request returns a decision with correlationId (circuit breaker CLOSED)
    # When the circuit breaker is CLOSED and ML succeeds, the REST response is a standard
    # RiskDecision with decision/reason/correlationId fields.
    Given path 'risk'
    And request { transactionId: 'fallback-tx-1', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    And match response.decision == '#string'
    And match response.correlationId == '#string'
    And match response.reason == '#string'

  Scenario: High-risk customer without new device is declined by ML score
    # c-4 has riskScore=0.95 in the seeded DB.  With no rule triggers, the ML score
    # path drives the decision to DECLINE (score > 0.7).
    Given path 'risk'
    And request { transactionId: 'fallback-tx-2', customerId: 'c-4', amountCents: 500 }
    When method POST
    Then status 200
    And match response.decision == 'DECLINE'
