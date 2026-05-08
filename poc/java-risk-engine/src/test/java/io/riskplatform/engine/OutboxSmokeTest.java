package io.riskplatform.engine;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.config.RiskApplicationFactory;
import io.riskplatform.engine.domain.entity.*;
import io.riskplatform.engine.domain.repository.OutboxRepository;
import io.riskplatform.engine.infrastructure.repository.log.ConsoleStructuredLogger;

import java.time.Duration;
import java.util.UUID;

/**
 * Smoke test verifying the outbox pattern lifecycle:
 * 1. After evaluate(), the event is appended to the outbox as PENDING.
 * 2. After flushAsync() + a short wait (≤ 500 ms), the event transitions to PUBLISHED.
 *
 * <p>This mirrors the production guarantee: the critical path only writes to the outbox
 * (same logical transaction as persisting the decision); the relay delivers asynchronously.
 */
public final class OutboxSmokeTest {

    public static void main(String[] args) throws Exception {
        try (var app = new RiskApplicationFactory()) {
            var correlationId = new CorrelationId(UUID.randomUUID().toString());
            var context = new ExecutionContext(
                    correlationId,
                    new ConsoleStructuredLogger().with("test", "outbox-smoke")
            );
            var request = new TransactionRiskRequest(
                    new TransactionId("tx-outbox-test"),
                    new CustomerId("user-outbox"),
                    new Money(5_000, "ARS"),
                    false,
                    correlationId,
                    new IdempotencyKey("outbox-smoke-" + UUID.randomUUID())
            );

            // ── Step 1: evaluate — writes to outbox as PENDING ──────────────
            app.evaluateRiskUseCase().evaluate(context, request, Duration.ofMillis(300));

            var outbox = app.outboxRepository();
            var pending = outbox.pending(10);
            assertThat(!pending.isEmpty(),
                    "After evaluate(), outbox must contain at least one PENDING event");

            var eventId = pending.getFirst().eventId();
            System.out.println("[OutboxSmokeTest] event in PENDING: " + eventId);

            // ── Step 2: flush + wait — relay must mark event PUBLISHED ───────
            app.outboxRelay().flushAsync();

            boolean published = awaitPublished(outbox, eventId, 500);
            assertThat(published,
                    "After flushAsync(), event must be PUBLISHED within 500 ms; eventId=" + eventId);

            System.out.println("[OutboxSmokeTest] event PUBLISHED: " + eventId);
        }
        System.out.println("OutboxSmokeTest OK");
    }

    private static boolean awaitPublished(
            OutboxRepository outbox,
            String eventId,
            long maxWaitMs
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            // If no pending events with this ID remain, it has been published.
            boolean stillPending = outbox.pending(1000).stream()
                    .anyMatch(e -> e.eventId().equals(eventId));
            if (!stillPending) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
