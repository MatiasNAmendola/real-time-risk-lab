package io.riskplatform.atdd.steps;

import io.cucumber.java.en.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Step definitions for features 01, 02, 03 — basic risk evaluation scenarios.
 */
public final class EvaluateRiskSteps {

    private final World world;

    public EvaluateRiskSteps(World world) {
        this.world = world;
    }

    // ---- Given ----

    @Given("a customer {string} with stable history")
    public void aCustomerWithStableHistory(String customerId) {
        world.customerId = customerId;
        // "stable history" means no chargebacks, age > 90 days by default
        // (seeded later if a specific age step overrides it)
        world.fixture.seedFeatures(customerId, 120, 0);
        world.newDevice = false;
    }

    @Given("a transaction {string} of {long} cents")
    public void aTransactionOfCents(String txId, long amountCents) {
        world.transactionId = txId;
        world.amountCents = amountCents;
    }

    @Given("a transaction {string} of {long} cents with idempotency key {string}")
    public void aTransactionWithIdempotencyKey(String txId, long amountCents, String idempotencyKey) {
        world.transactionId = txId;
        world.amountCents = amountCents;
        world.idempotencyKeyValue = idempotencyKey;
    }

    @Given("a transaction {string} of {long} cents from a new device")
    public void aTransactionFromNewDevice(String txId, long amountCents) {
        world.transactionId = txId;
        world.amountCents = amountCents;
        world.newDevice = true;
    }

    @Given("the customer account is {int} days old")
    public void theCustomerAccountIsOld(int ageDays) {
        world.customerAgeDays = ageDays;
        // Override the seed set by the "stable history" step
        if (world.customerId != null) {
            world.fixture.seedFeatures(world.customerId, ageDays, 0);
        }
    }

    @Given("the request carries correlation id {string}")
    public void theRequestCarriesCorrelationId(String correlationId) {
        world.correlationIdValue = correlationId;
    }

    // ---- When ----

    @When("the risk engine evaluates the transaction")
    public void theRiskEngineEvaluatesTheTransaction() {
        world.evaluate();
    }

    // ---- Then ----

    @Then("the decision is {string}")
    public void theDecisionIs(String expected) {
        assertThat(world.lastDecision).isNotNull();
        assertThat(world.lastDecision.decision().name())
                .as("decision value")
                .isEqualTo(expected);
    }

    @Then("the decision is not {string}")
    public void theDecisionIsNot(String unexpected) {
        assertThat(world.lastDecision).isNotNull();
        assertThat(world.lastDecision.decision().name())
                .as("decision should not be " + unexpected)
                .isNotEqualTo(unexpected);
    }

    @Then("the latency budget consumed less than {long} milliseconds")
    public void theLatencyBudgetConsumedLessThan(long millis) {
        // elapsed is measured by the use case itself
        assertThat(world.lastDecision.elapsed().toMillis())
                .as("elapsed ms")
                .isLessThan(millis);
    }

    @Then("the ML scorer was not invoked")
    public void theMLScorerWasNotInvoked() {
        // When rules fire first, the trace's modelVersion stays "not-called"
        assertThat(world.lastDecision.trace().modelVersion())
                .as("model version should be 'not-called' when ML was skipped")
                .isEqualTo("not-called");
    }
}
