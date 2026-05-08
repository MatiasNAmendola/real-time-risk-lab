@idempotency
Feature: Idempotent risk evaluation

  # Sending the same transactionId + idempotencyKey twice must return the
  # exact same response without re-running the evaluation pipeline.
  # The second call should be served from the IdempotencyStore (cache/DB).

  Background:
    * url baseUrl

  Scenario: Duplicate request with same idempotencyKey returns identical response
    * def txId = 'idem-tx-' + java.util.UUID.randomUUID()
    * def idemKey = java.util.UUID.randomUUID()

    # First call
    Given path 'risk'
    And header Idempotency-Key = idemKey
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    * def firstResponse = response
    * def firstCorrelationId = response.correlationId
    * def firstDecision = response.decision

    # Second call — identical payload and key
    Given path 'risk'
    And header Idempotency-Key = idemKey
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    And match response.decision == firstDecision
    And match response.correlationId == firstCorrelationId
    # The response must be structurally identical (same shape, same decision)
    And match response == firstResponse

  Scenario: Different idempotencyKey with same transactionId triggers a new evaluation
    * def txId = 'idem-tx2-' + java.util.UUID.randomUUID()

    Given path 'risk'
    And header Idempotency-Key = java.util.UUID.randomUUID()
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    * def correlationId1 = response.correlationId

    Given path 'risk'
    And header Idempotency-Key = java.util.UUID.randomUUID()
    And request { transactionId: '#(txId)', customerId: 'c-1', amountCents: 200000 }
    When method POST
    Then status 200
    # A different key = new evaluation = new correlationId
    And match response.correlationId != correlationId1
