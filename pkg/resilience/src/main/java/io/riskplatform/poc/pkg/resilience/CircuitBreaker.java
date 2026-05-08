package io.riskplatform.poc.pkg.resilience;

import java.time.Duration;

/**
 * Copied from poc/java-risk-engine with package re-mapped to io.riskplatform.poc.pkg.resilience.
 * Original: io.riskplatform.engine.infrastructure.resilience.CircuitBreaker
 *
 * Phase 2: apps will depend on this module and drop their local copy.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private int failures;
    private long openUntilNanos;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public synchronized boolean allowRequest() {
        return System.nanoTime() >= openUntilNanos;
    }

    public synchronized void success() {
        failures = 0;
        openUntilNanos = 0;
    }

    public synchronized void failure() {
        failures++;
        if (failures >= failureThreshold) {
            openUntilNanos = System.nanoTime() + openDuration.toNanos();
        }
    }
}
