package io.riskplatform.atdd.steps;

import io.cucumber.java.en.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Step definitions for feature 04 — idempotency.
 */
public final class IdempotencySteps {

    private final World world;

    public IdempotencySteps(World world) {
        this.world = world;
    }

    @When("the same request is sent again with the same idempotency key")
    public void theSameRequestIsSentAgain() {
        world.evaluateAgainSameKey();
    }

    @Then("both responses have the same decision")
    public void bothResponsesHaveTheSameDecision() {
        assertThat(world.lastDecision).isNotNull();
        assertThat(world.secondDecision).isNotNull();
        assertThat(world.secondDecision.decision())
                .as("second decision should equal first decision")
                .isEqualTo(world.lastDecision.decision());
        assertThat(world.secondDecision.reason())
                .as("second reason should equal first reason")
                .isEqualTo(world.lastDecision.reason());
    }

    @Then("the second evaluation did not invoke the rules engine")
    public void theSecondEvaluationDidNotInvokeTheRulesEngine() {
        // When idempotency cache is hit, the use case returns the stored decision.
        // We verify this by confirming the evaluatedRules list in the SECOND trace
        // is identical to the first (the cached RiskDecision object is returned as-is,
        // so both decisions are the same instance — same trace, same rules list).
        assertThat(world.secondDecision)
                .as("idempotency should return same RiskDecision instance")
                .isSameAs(world.lastDecision);
    }
}
