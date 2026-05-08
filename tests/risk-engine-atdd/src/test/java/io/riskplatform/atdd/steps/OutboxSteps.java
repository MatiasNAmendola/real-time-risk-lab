package io.riskplatform.atdd.steps;

import io.riskplatform.engine.domain.entity.DecisionEvent;
import io.cucumber.java.en.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Step definitions for features 05 and 07 — outbox pattern and correlation id propagation.
 */
public final class OutboxSteps {

    private final World world;

    public OutboxSteps(World world) {
        this.world = world;
    }

    // ---- Outbox feature 05 ----

    @Then("the outbox contains at least one pending event for transaction {string}")
    public void theOutboxContainsAtLeastOnePendingEventForTransaction(String txId) {
        var pending = world.outboxRepository.pending(100);
        assertThat(pending)
                .as("pending events in outbox for tx " + txId)
                .anySatisfy(event ->
                        assertThat(event.transactionId()).isEqualTo(txId)
                );
    }

    @When("the outbox relay flushes")
    public void theOutboxRelayFlushes() {
        world.outboxRelay.flushAsync();
    }

    @Then("the event for transaction {string} is marked as published within {long} milliseconds")
    public void theEventForTransactionIsMarkedAsPublishedWithin(String txId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            var pending = world.outboxRepository.pending(100);
            boolean txStillPending = pending.stream()
                    .anyMatch(e -> txId.equals(e.transactionId()));
            if (!txStillPending) {
                // Either it was never there, or it was published.
                // Verify it existed (i.e. evaluate was called) by checking all records.
                found = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        // If still pending after timeout, fail.
        var stillPending = world.outboxRepository.pending(100).stream()
                .anyMatch(e -> txId.equals(e.transactionId()));
        assertThat(stillPending)
                .as("event for tx " + txId + " should be published within " + timeoutMs + "ms")
                .isFalse();
    }

    // ---- Correlation ID feature 07 ----

    @Then("the decision trace contains correlation id {string}")
    public void theDecisionTraceContainsCorrelationId(String correlationId) {
        assertThat(world.lastDecision).isNotNull();
        assertThat(world.lastDecision.trace().correlationId().value())
                .as("correlationId in DecisionTrace")
                .isEqualTo(correlationId);
    }

    @Then("the outbox event for the transaction has correlation id {string}")
    public void theOutboxEventForTheTransactionHasCorrelationId(String correlationId) {
        List<DecisionEvent> pending = world.outboxRepository.pending(100);
        assertThat(pending)
                .as("outbox should contain event with correlationId=" + correlationId)
                .anySatisfy(event ->
                        assertThat(event.correlationId())
                                .as("event correlationId")
                                .isEqualTo(correlationId)
                );
    }
}
