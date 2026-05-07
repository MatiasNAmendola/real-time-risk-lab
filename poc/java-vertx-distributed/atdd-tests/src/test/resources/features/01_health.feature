@health
Feature: Health check

  # Quick smoke test.  If this fails, all other tests will also fail because
  # the service is not reachable.  Provides a clear error message pointing to
  # the root cause rather than a cryptic connection refused.

  Background:
    * url baseUrl

  Scenario: GET /health returns 200 and UP status
    Given path 'health'
    When method GET
    Then status 200
    And match response.status == 'UP'

  Scenario: GET /health/live returns 200
    Given path 'health', 'live'
    When method GET
    Then status 200

  Scenario: GET /health/ready returns 200 when all deps are up
    Given path 'health', 'ready'
    When method GET
    Then status 200
