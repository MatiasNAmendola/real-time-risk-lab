package io.riskplatform.engine.application.usecase.risk;

import io.riskplatform.engine.application.port.out.CircuitBreakerPort;
import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.application.common.TransactionManager;
import io.riskplatform.engine.domain.entity.*;
import io.riskplatform.engine.domain.repository.*;
import io.riskplatform.engine.domain.service.*;
import io.riskplatform.engine.domain.usecase.EvaluateRiskUseCase;

import java.time.Duration;

public final class EvaluateTransactionRiskService implements EvaluateRiskUseCase {
    private static final String RULE_SET_VERSION = "ruleset-2026-05-07";
    private static final Duration MIN_MODEL_BUDGET = Duration.ofMillis(80);

    private final RuleBasedDecisionPolicy rulePolicy;
    private final ScoreDecisionPolicy scorePolicy;
    private final FallbackDecisionPolicy fallbackPolicy;
    private final FeatureProvider featureProvider;
    private final RiskModelScorer riskModelScorer;
    private final CircuitBreakerPort modelCircuitBreaker;
    private final DecisionEventPublisher eventPublisher;
    private final DecisionIdempotencyStore idempotencyStore;
    private final RiskDecisionRepository riskDecisionRepository;
    private final TransactionManager transactionManager;
    private final ClockPort clock;

    public EvaluateTransactionRiskService(
            RuleBasedDecisionPolicy rulePolicy,
            ScoreDecisionPolicy scorePolicy,
            FallbackDecisionPolicy fallbackPolicy,
            FeatureProvider featureProvider,
            RiskModelScorer riskModelScorer,
            CircuitBreakerPort modelCircuitBreaker,
            DecisionEventPublisher eventPublisher,
            DecisionIdempotencyStore idempotencyStore,
            RiskDecisionRepository riskDecisionRepository,
            TransactionManager transactionManager,
            ClockPort clock
    ) {
        this.rulePolicy = rulePolicy;
        this.scorePolicy = scorePolicy;
        this.fallbackPolicy = fallbackPolicy;
        this.featureProvider = featureProvider;
        this.riskModelScorer = riskModelScorer;
        this.modelCircuitBreaker = modelCircuitBreaker;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.riskDecisionRepository = riskDecisionRepository;
        this.transactionManager = transactionManager;
        this.clock = clock;
    }

    @Override
    public RiskDecision evaluate(ExecutionContext context, TransactionRiskRequest request, Duration maxLatency) {
        var log = context.logger().with("transaction_id", request.transactionId().value()).with("correlation_id", request.correlationId().value());
        var requestContext = new ExecutionContext(request.correlationId(), log);
        var cached = idempotencyStore.find(request.idempotencyKey());
        if (cached.isPresent()) {
            log.info("idempotent retry detected", "idempotency_key", request.idempotencyKey().value());
            return cached.get();
        }

        long startedNanos = System.nanoTime();
        var budget = new LatencyBudget(maxLatency);
        var trace = new DecisionTrace(request.correlationId(), RULE_SET_VERSION, clock.now());

        var features = featureProvider.getFeatures(request);
        var decision = rulePolicy.firstMatch(request, features, trace)
                .map(match -> new DecisionDraft(match.decision(), match.reason()))
                .orElseGet(() -> evaluateModelOrFallback(request, features, budget, trace));

        var result = new RiskDecision(
                request.transactionId(),
                decision.decision(),
                decision.reason(),
                Duration.ofNanos(System.nanoTime() - startedNanos),
                trace
        );

        return transactionManager.inTransaction(requestContext, () -> {
            var stored = idempotencyStore.saveIfAbsent(request.idempotencyKey(), result);
            if (stored == result) {
                riskDecisionRepository.create(requestContext, result);
                eventPublisher.publish(DecisionEvent.from(request, result, clock.now()));
            }
            return stored;
        });
    }

    private DecisionDraft evaluateModelOrFallback(
            TransactionRiskRequest request,
            FeatureSnapshot features,
            LatencyBudget budget,
            DecisionTrace trace
    ) {
        if (!modelCircuitBreaker.allowRequest()) {
            trace.recordFallback("ml-circuit-open");
            return fallback(request, features, "ml-unavailable-fallback");
        }
        if (!budget.hasAtLeast(MIN_MODEL_BUDGET)) {
            trace.recordFallback("insufficient-budget-for-ml remainingMs=" + budget.remaining().toMillis());
            return fallback(request, features, "latency-budget-fallback");
        }
        try {
            var score = riskModelScorer.score(request, features, budget.remaining().minusMillis(20));
            trace.recordScore(score);
            modelCircuitBreaker.success();
            var scoreDecision = scorePolicy.classify(score);
            return new DecisionDraft(scoreDecision.decision(), scoreDecision.reason());
        } catch (Exception ex) {
            modelCircuitBreaker.failure();
            trace.recordFallback("ml-error=" + ex.getMessage());
            return fallback(request, features, "ml-error-fallback");
        }
    }

    private DecisionDraft fallback(TransactionRiskRequest request, FeatureSnapshot features, String technicalReason) {
        var fallback = fallbackPolicy.decide(request, features);
        return new DecisionDraft(fallback.decision(), technicalReason + ":" + fallback.reason());
    }

    private record DecisionDraft(Decision decision, String reason) {}
}
