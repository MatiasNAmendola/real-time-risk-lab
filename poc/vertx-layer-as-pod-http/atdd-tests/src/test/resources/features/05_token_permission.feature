@security @tokens
Feature: Token-based permission model

  # Verifies that the token-based permission model enforces least privilege.
  #
  # The model has two tokens:
  #   CONTROLLER_TO_USECASE_TOKEN from env, scope: risk:evaluate
  #   USECASE_TO_REPOSITORY_TOKEN from env, scope: repository:rw
  #
  # Key invariant: controller does NOT own the repository token.
  # If controller tries to call repository directly, the repository returns 403.
  #
  # This is the HTTP+tokens analog of network-level isolation in vertx-distributed.
  # Trade-off: tokens are simpler to understand and implement;
  #            networks provide OS-level enforcement.

  Background:
    * def controllerToken = java.lang.System.getenv('CONTROLLER_TO_USECASE_TOKEN') || 'change-me-controller-usecase-token'
    * def repositoryToken = java.lang.System.getenv('USECASE_TO_REPOSITORY_TOKEN') || 'change-me-usecase-repository-token'

  Scenario: usecase-pod internal endpoint rejects request without token
    Given url 'http://localhost:8181'
    Given path '/internal/risk/evaluate'
    And request
      """
      {
        "transactionId": "tx-notoken-1",
        "customerId": "c-1",
        "amountInCents": 1000,
        "newDevice": false,
        "correlationId": "corr-notoken",
        "idempotencyKey": "ik-notoken-1"
      }
      """
    When method POST
    Then status 403

  Scenario: usecase-pod internal endpoint rejects wrong token
    Given url 'http://localhost:8181'
    Given path '/internal/risk/evaluate'
    And header x-pod-token = 'wrong-token-entirely'
    And header x-pod-scopes = 'risk:evaluate'
    And request
      """
      {
        "transactionId": "tx-badtoken-1",
        "customerId": "c-1",
        "amountInCents": 1000,
        "newDevice": false,
        "correlationId": "corr-badtoken",
        "idempotencyKey": "ik-badtoken-1"
      }
      """
    When method POST
    Then status 403

  Scenario: repository-pod blocks controller token (least-privilege enforcement)
    # Controller owns CONTROLLER_TO_USECASE_TOKEN for risk:evaluate scope.
    # Repository only accepts USECASE_TO_REPOSITORY_TOKEN for repository:rw scope.
    # Presenting the controller's token to the repository must return 403.
    Given url 'http://localhost:8182'
    Given path '/internal/idempotency/find'
    And header x-pod-token = controllerToken
    And header x-pod-scopes = 'risk:evaluate'
    And request { idempotencyKey: 'ik-bypass-attempt' }
    When method POST
    Then status 403

  Scenario: repository-pod accepts usecase token with correct scope
    Given url 'http://localhost:8182'
    Given path '/internal/idempotency/find'
    And header x-pod-token = repositoryToken
    And header x-pod-scopes = 'repository:rw'
    And request { idempotencyKey: 'ik-legit-lookup' }
    When method POST
    Then status 200
    And match response.found == false
