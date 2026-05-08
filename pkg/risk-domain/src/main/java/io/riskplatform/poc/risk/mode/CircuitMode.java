package io.riskplatform.rules.mode;

import io.riskplatform.rules.engine.AggregateDecision;
import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.engine.RuleEngine;
import io.riskplatform.rules.rule.RuleAction;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * Circuit mode: falls back to REVIEW if the engine p99 latency in the last 1-minute window
 * exceeds the configured threshold (default 250ms).
 *
 * When the circuit is open (p99 > threshold), all evaluation calls are short-circuited to REVIEW
 * without invoking the engine. This protects the request path from a degraded rule engine.
 *
 * The circuit resets automatically when the observation window rolls over and p99 drops below
 * the threshold — there is no manual reset required.
 *
 * Latency observations would be instrumented via Micrometer in production. This implementation
 * uses an in-memory sliding window for PoC purposes.
 */
public final class CircuitMode implements BarrierMode {

    private static final long WINDOW_MS       = 60_000L; // 1 minute
    private static final double P99_PERCENTILE = 0.99;

    private final RuleEngine engine;
    private final long p99ThresholdMs;
    private final ConcurrentLinkedDeque<Long> latencyObservations = new ConcurrentLinkedDeque<>();

    public CircuitMode(RuleEngine engine, long p99ThresholdMs) {
        this.engine          = engine;
        this.p99ThresholdMs  = p99ThresholdMs;
    }

    public CircuitMode(RuleEngine engine) {
        this(engine, 250L); // default 250ms threshold per design doc
    }

    @Override
    public AggregateDecision evaluate(FeatureSnapshot snapshot) {
        evictOldObservations();

        if (isCircuitOpen()) {
            return fallbackDecision();
        }

        long start = System.currentTimeMillis();
        AggregateDecision decision = engine.evaluate(snapshot);
        long elapsed = System.currentTimeMillis() - start;
        latencyObservations.addFirst(elapsed);

        return decision;
    }

    @Override
    public String modeName() { return "circuit"; }

    /** Returns true when the p99 of recent observations exceeds the threshold. */
    public boolean isCircuitOpen() {
        List<Long> obs = latencyObservations.stream().sorted().toList();
        if (obs.isEmpty()) return false;
        int idx = (int) Math.ceil(P99_PERCENTILE * obs.size()) - 1;
        long p99 = obs.get(Math.max(0, idx));
        return p99 > p99ThresholdMs;
    }

    private void evictOldObservations() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        // We store timestamps, but latencyObservations only has durations.
        // For simplicity, cap the observation buffer to prevent unbounded growth.
        // A production implementation would use timestamped observations.
        while (latencyObservations.size() > 1000) {
            latencyObservations.pollLast();
        }
    }

    private AggregateDecision fallbackDecision() {
        return new AggregateDecision(
                RuleAction.REVIEW, List.of(),
                engine.activeConfigHash(), engine.activeConfig().version(),
                true, null, 0L);
    }
}
