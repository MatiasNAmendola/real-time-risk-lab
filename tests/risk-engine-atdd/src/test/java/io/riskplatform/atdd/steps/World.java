package io.riskplatform.atdd.steps;

import io.riskplatform.atdd.support.RiskApplicationFixture;
import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.application.common.StructuredLogger;
import io.riskplatform.engine.domain.entity.*;
import io.riskplatform.engine.domain.usecase.EvaluateRiskUseCase;
import io.riskplatform.engine.infrastructure.repository.event.InMemoryOutboxRepository;
import io.riskplatform.engine.infrastructure.repository.event.OutboxRelay;
import io.riskplatform.engine.infrastructure.repository.log.ConsoleStructuredLogger;

import java.time.Duration;
import java.util.UUID;

/**
 * Shared context (World) injected by PicoContainer into all step definition classes.
 * One fresh instance per scenario.
 */
public final class World implements AutoCloseable {

    // ------- application fixture (one per scenario) -------
    public final RiskApplicationFixture fixture;
    public final EvaluateRiskUseCase useCase;
    public final InMemoryOutboxRepository outboxRepository;
    public final OutboxRelay outboxRelay;
    private final StructuredLogger logger;

    // ------- scenario state -------
    public String customerId;
    public String transactionId;
    public long amountCents;
    public boolean newDevice = false;
    public String correlationIdValue;
    public String idempotencyKeyValue;
    public int customerAgeDays = -1; // -1 means use hash-based default

    // results
    public RiskDecision lastDecision;
    public RiskDecision secondDecision;

    public World() {
        this.fixture = new RiskApplicationFixture();
        this.useCase = fixture.evaluateRiskUseCase();
        this.outboxRepository = fixture.outboxRepository();
        this.outboxRelay = fixture.outboxRelay();
        this.logger = new ConsoleStructuredLogger(true).with("service", "atdd");
    }

    /** Build and evaluate the request. Result stored in {@link #lastDecision}. */
    public RiskDecision evaluate() {
        var corrId = correlationIdValue != null
                ? new CorrelationId(correlationIdValue)
                : new CorrelationId("atdd-corr-" + UUID.randomUUID());
        var idempKey = idempotencyKeyValue != null
                ? new IdempotencyKey(idempotencyKeyValue)
                : new IdempotencyKey(transactionId + "-" + UUID.randomUUID());

        if (customerAgeDays >= 0) {
            fixture.seedFeatures(customerId, customerAgeDays, 0);
        }

        var request = new TransactionRiskRequest(
                new TransactionId(transactionId),
                new CustomerId(customerId),
                new Money(amountCents, "ARS"),
                newDevice,
                corrId,
                idempKey
        );
        var context = new ExecutionContext(corrId, logger.with("scenario", transactionId));
        lastDecision = useCase.evaluate(context, request, Duration.ofMillis(200));
        return lastDecision;
    }

    /** Build and evaluate a second call using the SAME idempotency key. */
    public RiskDecision evaluateAgainSameKey() {
        // Same idempotency key, same transaction id => store returns cached decision
        var corrId = correlationIdValue != null
                ? new CorrelationId(correlationIdValue)
                : new CorrelationId("atdd-corr2-" + UUID.randomUUID());

        var request = new TransactionRiskRequest(
                new TransactionId(transactionId),
                new CustomerId(customerId),
                new Money(amountCents, "ARS"),
                newDevice,
                corrId,
                new IdempotencyKey(idempotencyKeyValue)
        );
        var context = new ExecutionContext(corrId, logger.with("scenario", transactionId + "-retry"));
        secondDecision = useCase.evaluate(context, request, Duration.ofMillis(200));
        return secondDecision;
    }

    @Override
    public void close() throws Exception {
        fixture.close();
    }
}
