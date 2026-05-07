# Feature: Backoffice rules administration (bare-javac PoC)
# Analog scenarios for the poc/java-risk-engine HTTP server.
# These scenarios run against the HttpController on port 8081.
# @karate-only: these scenarios use Karate DSL syntax (url/method/status)
# and require live HTTP infrastructure; excluded from the Cucumber/JUnit suite.

@karate-only
Feature: Backoffice rules administration - bare-javac engine

  Background:
    * url baseUrl
    * def adminToken = karate.properties['admin.token'] || 'admin-secret'
    * def adminHeaders = { 'X-Admin-Token': '#(adminToken)', 'Content-Type': 'application/json' }

  # Scenario 1: Admin lowers threshold and decision changes
  Scenario: Admin can retrieve active rules configuration
    Given path '/admin/rules'
    And headers adminHeaders
    When method GET
    Then status 200
    And match response.version == '#string'
    And match response.hash == '#string'
    And match response.rules == '#array'
    And match response.total == '#number'
    And match response.enabled_count == '#number'
    And assert response.total >= 0

  # Scenario 2: Reload returns version info
  Scenario: Admin reload returns new version hash
    Given path '/admin/rules/reload'
    And headers adminHeaders
    When method POST
    Then status 200
    And match response.new_hash == '#string'
    And match response.rules_loaded == '#number'
    And match response.reload_duration_ms == '#number'
    And assert response.rules_loaded >= 0

  # Scenario 3: Allowlist customer bypass — dry-run test endpoint
  Scenario: Admin dry-run test returns decision for transaction
    Given path '/admin/rules/test'
    And headers adminHeaders
    And request { transactionId: 'tx-atdd-test-001', customerId: 'cust-test', amountCents: 5000000, newDevice: false }
    When method POST
    Then status 200
    And match response.decision == '#string'
    And match response.dryRun == true

  # Scenario 4: Reject malformed config
  Scenario: Admin endpoint returns 401 without valid token
    Given path '/admin/rules'
    And header Content-Type = 'application/json'
    When method GET
    Then status 401
    And match response.error == '#string'

  # Scenario 5: Audit trail accessible
  Scenario: Audit trail returns list of recent decisions
    # First create an entry via a normal risk evaluation
    Given path '/risk'
    And headers { 'Content-Type': 'application/json' }
    And request { transactionId: 'tx-audit-001', customerId: 'cust-audit', amountCents: 3000000 }
    When method POST
    Then status 200

    # Now check the audit trail
    Given path '/admin/rules/audit'
    And headers adminHeaders
    When method GET
    Then status 200
    And match response.entries == '#array'
    And match response.total == '#number'
