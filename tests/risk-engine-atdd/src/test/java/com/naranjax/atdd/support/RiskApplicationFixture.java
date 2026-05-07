package com.naranjax.atdd.support;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.application.common.StructuredLogger;
import com.naranjax.interview.risk.application.usecase.risk.EvaluateTransactionRiskService;
import com.naranjax.interview.risk.config.RiskApplicationFactory;
import com.naranjax.interview.risk.domain.entity.CorrelationId;
import com.naranjax.interview.risk.domain.rule.HighAmountRule;
import com.naranjax.interview.risk.domain.rule.NewDeviceYoungCustomerRule;
import com.naranjax.interview.risk.domain.service.*;
import com.naranjax.interview.risk.domain.usecase.EvaluateRiskUseCase;
import com.naranjax.interview.risk.infrastructure.repository.event.*;
import com.naranjax.interview.risk.infrastructure.repository.feature.InMemoryFeatureProvider; // referenced by canonical factory only
import com.naranjax.interview.risk.infrastructure.repository.idempotency.InMemoryDecisionIdempotencyStore;
import com.naranjax.interview.risk.infrastructure.repository.log.ConsoleStructuredLogger;
import com.naranjax.interview.risk.infrastructure.repository.ml.FakeRiskModelScorer;
import com.naranjax.interview.risk.infrastructure.repository.persistence.*;
import com.naranjax.interview.risk.infrastructure.resilience.CircuitBreaker;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Bootstrap fixture for ATDD tests.
 * <p>
 * Mirrors {@link RiskApplicationFactory} but wires an injected {@link ClockStub}
 * so tests get deterministic timestamps and a handle to the in-memory outbox.
 * <p>
 * NOTE ON ML TIMEOUT SEAM: The current PoC's {@code FakeRiskModelScorer} picks a random
 * latency at runtime and does not expose a constructor parameter to force a timeout.
 * {@link RiskApplicationFactory} also hardcodes its instantiation. Therefore the
 * {@code @wip} scenario in feature 06 cannot be implemented without modifying the PoC.
 * This fixture documents the limitation: to enable ML-timeout testing, the PoC should
 * expose a {@code RiskApplicationFactory(ClockPort, RiskModelScorer)} constructor or a
 * builder that allows injecting a stub scorer.
 */
public final class RiskApplicationFixture implements AutoCloseable {

    private final InMemoryOutboxRepository outboxRepository;
    private final OutboxRelay outboxRelay;
    private final EvaluateRiskUseCase evaluateRiskUseCase;
    private final ConfigurableFeatureProvider featureProvider;
    private final ClockStub clock;

    public RiskApplicationFixture() {
        this.clock = new ClockStub();
        this.featureProvider = new ConfigurableFeatureProvider();
        this.outboxRepository = new InMemoryOutboxRepository();
        this.outboxRelay = new OutboxRelay(outboxRepository, Executors.newVirtualThreadPerTaskExecutor());

        var bootstrapLogger = new ConsoleStructuredLogger(true)
                .with("service", "risk-atdd");
        var bootstrapContext = new ExecutionContext(
                new CorrelationId("atdd-bootstrap-" + UUID.randomUUID()),
                bootstrapLogger
        );
        var eventPublisher = new OutboxDecisionEventPublisher(outboxRepository, bootstrapContext);

        this.evaluateRiskUseCase = new EvaluateTransactionRiskService(
                new RuleBasedDecisionPolicy(List.of(new HighAmountRule(), new NewDeviceYoungCustomerRule())),
                new ScoreDecisionPolicy(),
                new FallbackDecisionPolicy(),
                featureProvider,
                new FakeRiskModelScorer(),
                new CircuitBreaker(3, Duration.ofSeconds(3)),
                eventPublisher,
                new InMemoryDecisionIdempotencyStore(),
                new InMemoryRiskDecisionRepository(),
                new InMemoryTransactionManager(),
                clock
        );
    }

    public EvaluateRiskUseCase evaluateRiskUseCase() { return evaluateRiskUseCase; }
    public OutboxRelay outboxRelay() { return outboxRelay; }
    public InMemoryOutboxRepository outboxRepository() { return outboxRepository; }
    public ClockStub clock() { return clock; }

    /**
     * Pre-populate the feature store for a customer so tests can control
     * {@code customerAgeDays} (needed for the NewDeviceYoungCustomerRule).
     * The InMemoryFeatureProvider computes age from customer id hashcode by default;
     * this method lets ATDD tests override that to specific values.
     */
    /** Pre-populate features for a customer to control rule evaluation in tests. */
    public void seedFeatures(String customerId, int customerAgeDays, int chargebackCount90d) {
        featureProvider.seed(customerId, customerAgeDays, chargebackCount90d);
    }

    @Override
    public void close() throws Exception {
        outboxRelay.close();
    }
}
