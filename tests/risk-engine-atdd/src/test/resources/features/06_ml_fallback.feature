@ml-fallback @wip
Feature: ML model fallback on timeout

  Acceptance criteria:
  Cuando el modelo ML excede el presupuesto de latencia,
  el motor debe caer al FallbackDecisionPolicy sin exponer el error al caller.

  # NOTE: FakeRiskModelScorer simulates random latency (20-160ms) and throws
  # ModelTimeoutException when timeout < simulatedLatencyMs. To reliably trigger
  # a timeout we would need to inject a configurable latency stub. The current
  # RiskApplicationFactory does not expose a seam to replace FakeRiskModelScorer.
  # Marked @wip until the PoC exposes that seam (without modifying it here).

  @wip
  Scenario: ML scorer times out and fallback policy is used
    Given a customer "c-5" with stable history
    And a transaction "tx-ml-timeout" of 3000 cents
    And the ML scorer is configured to always time out
    When the risk engine evaluates the transaction
    Then the decision is not an error
    And the trace records a fallback reason containing "ml-timeout"
