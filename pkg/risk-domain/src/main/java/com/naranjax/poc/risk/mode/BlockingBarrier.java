package com.naranjax.poc.risk.mode;

import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.engine.RuleEngine;

/**
 * Default barrier mode: the response waits for the rule engine to return a decision.
 *
 * This is the standard operating mode. The caller is blocked until evaluation completes
 * or the engine's configured timeout_ms is reached (in which case fallback_decision is used).
 *
 * Use this mode in production unless shadow or circuit mode is explicitly configured.
 */
public final class BlockingBarrier implements BarrierMode {

    private final RuleEngine engine;

    public BlockingBarrier(RuleEngine engine) {
        this.engine = engine;
    }

    @Override
    public AggregateDecision evaluate(FeatureSnapshot snapshot) {
        return engine.evaluate(snapshot);
    }

    @Override
    public String modeName() { return "blocking"; }
}
