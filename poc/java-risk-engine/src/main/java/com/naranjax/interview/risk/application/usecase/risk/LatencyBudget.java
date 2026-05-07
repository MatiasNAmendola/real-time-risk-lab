package com.naranjax.interview.risk.application.usecase.risk;

import java.time.Duration;

public final class LatencyBudget {
    private final long deadlineNanos;

    public LatencyBudget(Duration maxLatency) {
        this.deadlineNanos = System.nanoTime() + maxLatency.toNanos();
    }

    public boolean hasAtLeast(Duration duration) {
        return remaining().compareTo(duration) >= 0;
    }

    public Duration remaining() {
        return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
    }
}
