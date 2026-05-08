package io.riskplatform.engine.infrastructure.resilience;

import io.riskplatform.engine.application.port.out.CircuitBreakerPort;

import java.time.Duration;

/**
 * Concrete circuit-breaker implementation.
 * Implements {@link CircuitBreakerPort} so the application layer can depend
 * on the port interface rather than this concrete class (fixes ArchUnit Rule 4).
 */
public final class CircuitBreaker implements CircuitBreakerPort {
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
