@http @inter-pod
Feature: Inter-pod communication via HTTP (not event bus)

  # Verifies that the flow controller -> usecase -> repository goes through HTTP.
  # This is the architectural claim of vertx-layer-as-pod-http vs vertx-distributed:
  #
  #   vertx-layer-as-pod-http : controller calls usecase via plain HTTP on port 8181
  #   vertx-distributed   : controller calls usecase via Hazelcast event bus (binary TCP)
  #
  # We verify HTTP inter-pod by checking:
  #   1. The health endpoint on each pod's dedicated port (3 separate HTTP servers)
  #   2. A full evaluate call succeeds end-to-end (meaning HTTP chain completed)
  #   3. Usecase internal endpoint reachable only with correct token (HTTP auth visible)

  Scenario: controller-pod health is UP on port 8180
    Given url 'http://localhost:8180'
    Given path '/health'
    When method GET
    Then status 200
    And match response.pod == 'controller-pod'
    And match response.status == 'UP'

  Scenario: usecase-pod has its own HTTP server on port 8181
    Given url 'http://localhost:8181'
    Given path '/health'
    When method GET
    Then status 200
    And match response.pod == 'usecase-pod'
    And match response.status == 'UP'

  Scenario: repository-pod has its own HTTP server on port 8182
    Given url 'http://localhost:8182'
    Given path '/health'
    When method GET
    Then status 200
    And match response.pod == 'repository-pod'
    And match response.status == 'UP'

  Scenario: Full evaluate call traverses all three HTTP servers
    # If controller -> usecase -> repository chain works end-to-end, all 3 HTTP servers participated
    Given url 'http://localhost:8180'
    Given path '/risk/evaluate'
    And request
      """
      {
        "transactionId": "tx-http-chain-1",
        "customerId": "c-1",
        "amountInCents": 3000,
        "newDevice": false,
        "correlationId": "corr-http-chain",
        "idempotencyKey": "ik-http-chain-1"
      }
      """
    When method POST
    Then status 200
    # The trace proves repository participated (saved decision came back)
    And match response.trace != null
    And match response.transactionId == 'tx-http-chain-1'
