package com.naranjax.interview.risk;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.domain.entity.*;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;

import java.time.Duration;
import java.util.UUID;

public final class ArchitectureSmokeTest {
    public static void main(String[] args) throws Exception {
        try (var app = new RiskApplicationFactory()) {
            var correlationId = new CorrelationId(UUID.randomUUID().toString());
            var context = new ExecutionContext(correlationId, new ConsoleStructuredLogger().with("test", "architecture-smoke"));
            var request = new TransactionRiskRequest(
                    new TransactionId("tx-test"),
                    new CustomerId("user-test"),
                    new Money(70_000, "ARS"),
                    false,
                    correlationId,
                    new IdempotencyKey("same-retry-key")
            );
            var first = app.evaluateRiskUseCase().evaluate(context, request, Duration.ofMillis(300));
            var retry = app.evaluateRiskUseCase().evaluate(context, request, Duration.ofMillis(300));

            assertThat(first.decision() == Decision.REVIEW, "high amount should go to REVIEW");
            assertThat(first == retry, "idempotent retry should return stored decision instance");
            assertThat(first.trace().evaluatedRules().contains("high-amount-v1=true"), "trace should include matching rule");
        }
        System.out.println("ArchitectureSmokeTest OK");
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
