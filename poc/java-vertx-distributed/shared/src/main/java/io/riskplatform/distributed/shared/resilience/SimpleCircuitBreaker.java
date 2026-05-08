package io.riskplatform.distributed.shared.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal count-based circuit breaker compatible with the Vert.x event-loop threading model.
 *
 * <p>States:
 * <ul>
 *   <li>CLOSED   – normal operation; failures are counted.
 *   <li>OPEN     – fast-fail; no requests allowed until {@code resetTimeoutMillis} elapses.
 *   <li>HALF_OPEN – one probe request is allowed; success closes the breaker again.
 * </ul>
 *
 * <p>All fields use atomic primitives so the breaker is safe to share across event-loop threads
 * without additional synchronisation.
 */
public final class SimpleCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int  failureThreshold;
    private final long resetTimeoutMillis;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong    openedAt            = new AtomicLong(0);
    private volatile State      state               = State.CLOSED;

    /**
     * @param failureThreshold    number of consecutive failures before opening.
     * @param resetTimeoutMillis  milliseconds to wait in OPEN before moving to HALF_OPEN.
     */
    public SimpleCircuitBreaker(int failureThreshold, long resetTimeoutMillis) {
        this.failureThreshold   = failureThreshold;
        this.resetTimeoutMillis = resetTimeoutMillis;
    }

    /**
     * Returns {@code true} if the caller is allowed to proceed with the protected operation.
     * When OPEN and the reset timeout has elapsed, the breaker transitions to HALF_OPEN and
     * returns {@code true} for a single probe request.
     */
    public boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() > resetTimeoutMillis) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    /** Must be called after every successful invocation of the protected operation. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
    }

    /** Must be called after every failed invocation of the protected operation. */
    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            state    = State.OPEN;
            openedAt.set(System.currentTimeMillis());
        }
    }

    public State currentState() {
        return state;
    }
}
