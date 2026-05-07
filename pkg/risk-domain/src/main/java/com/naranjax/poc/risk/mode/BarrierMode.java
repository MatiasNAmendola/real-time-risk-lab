package com.naranjax.poc.risk.mode;

import com.naranjax.poc.risk.engine.AggregateDecision;
import com.naranjax.poc.risk.engine.FeatureSnapshot;

/**
 * Strategy interface for how the rules engine evaluation is integrated into the request path.
 *
 * Three implementations:
 * - BlockingBarrier: blocks the response until the engine returns a decision (default).
 * - ShadowMode: responds immediately with APPROVE; evaluates in background for offline analysis.
 * - CircuitMode: falls back to REVIEW if the engine p99 latency exceeds a threshold.
 *
 * Selected via env var BARRIER_MODE=blocking|shadow|circuit.
 */
public interface BarrierMode {

    /**
     * Evaluates the snapshot and returns a decision according to this mode's strategy.
     *
     * @param snapshot pre-materialised feature signals
     * @return the decision to return to the caller
     */
    AggregateDecision evaluate(FeatureSnapshot snapshot);

    /** Name of the mode for observability and logging. */
    String modeName();
}
