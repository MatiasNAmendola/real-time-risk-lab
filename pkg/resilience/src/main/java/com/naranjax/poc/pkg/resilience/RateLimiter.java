package com.naranjax.poc.pkg.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter. Thread-safe, no external dependencies.
 */
public final class RateLimiter {

    private final int permitsPerPeriod;
    private final long periodNanos;
    private final AtomicLong nextRefillNanos;
    private volatile int availablePermits;

    public RateLimiter(int permitsPerPeriod, Duration period) {
        this.permitsPerPeriod = permitsPerPeriod;
        this.periodNanos = period.toNanos();
        this.availablePermits = permitsPerPeriod;
        this.nextRefillNanos = new AtomicLong(System.nanoTime() + periodNanos);
    }

    public synchronized boolean tryAcquire() {
        refillIfDue();
        if (availablePermits > 0) {
            availablePermits--;
            return true;
        }
        return false;
    }

    private void refillIfDue() {
        long now = System.nanoTime();
        if (now >= nextRefillNanos.get()) {
            availablePermits = permitsPerPeriod;
            nextRefillNanos.set(now + periodNanos);
        }
    }
}
