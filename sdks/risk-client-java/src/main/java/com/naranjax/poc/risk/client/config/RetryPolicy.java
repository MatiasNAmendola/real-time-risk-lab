package com.naranjax.poc.risk.client.config;

import java.time.Duration;

/**
 * Retry configuration for transient failures in the REST channel.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;

    private RetryPolicy(int maxAttempts, Duration initialDelay, double multiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
    }

    /** No retries — fail immediately on first error. */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ZERO, 1.0);
    }

    /** Exponential backoff: 3 attempts, initial 100 ms, multiplier 2. */
    public static RetryPolicy exponentialBackoff() {
        return new RetryPolicy(3, Duration.ofMillis(100), 2.0);
    }

    /** Fixed-delay retry. */
    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new RetryPolicy(maxAttempts, delay, 1.0);
    }

    public int maxAttempts()    { return maxAttempts; }
    public Duration initialDelay() { return initialDelay; }
    public double multiplier()  { return multiplier; }
}
