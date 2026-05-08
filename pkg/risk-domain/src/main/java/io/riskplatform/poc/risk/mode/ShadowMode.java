package io.riskplatform.rules.mode;

import io.riskplatform.rules.engine.AggregateDecision;
import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.engine.RuleEngine;
import io.riskplatform.rules.rule.RuleAction;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Shadow mode: responds immediately with APPROVE; evaluates in background for offline analysis.
 *
 * Use this mode when rolling out a new rule set and validating its behavior without impacting
 * real traffic. The background decision is published to the shadow-decisions consumer for
 * comparison against what production would have decided.
 *
 * The caller always receives APPROVE — this mode never blocks or declines a transaction.
 *
 * In a Vert.x/Kafka environment, the shadowPublisher callback emits to topic risk-shadow-decisions.
 */
public final class ShadowMode implements BarrierMode {

    private final RuleEngine engine;
    private final ExecutorService executor;
    private final Consumer<AggregateDecision> shadowPublisher;

    public ShadowMode(RuleEngine engine, Consumer<AggregateDecision> shadowPublisher) {
        this.engine          = engine;
        this.shadowPublisher = shadowPublisher;
        this.executor        = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public AggregateDecision evaluate(FeatureSnapshot snapshot) {
        // Respond immediately with a safe default
        AggregateDecision immediate = new AggregateDecision(
                RuleAction.APPROVE, List.of(),
                engine.activeConfigHash(), engine.activeConfig().version(),
                false, null, 0L);

        // Evaluate in background; publish shadow result for offline analysis
        executor.submit(() -> {
            try {
                AggregateDecision shadow = engine.evaluate(snapshot);
                shadowPublisher.accept(shadow);
            } catch (Exception e) {
                // Shadow evaluation failures are non-fatal
                System.err.println("[ShadowMode] Background evaluation failed: " + e.getMessage());
            }
        });

        return immediate;
    }

    @Override
    public String modeName() { return "shadow"; }
}
