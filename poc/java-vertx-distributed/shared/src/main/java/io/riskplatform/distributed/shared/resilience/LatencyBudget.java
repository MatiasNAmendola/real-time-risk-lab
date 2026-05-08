package io.riskplatform.distributed.shared.resilience;

/**
 * Tracks a wall-clock latency budget from the moment of construction.
 *
 * <p>Callers use this to decide whether there is enough time left to invoke an optional
 * downstream call (e.g. the ML scorer) before the end-to-end SLA deadline is breached.
 *
 * <pre>{@code
 * LatencyBudget budget = new LatencyBudget(280); // 280 ms total
 * if (budget.canSpend(80)) {
 *     double score = invokeMLScorer(budget.remainingMillis() - 20);
 * }
 * }</pre>
 */
public final class LatencyBudget {

    private final long startNanos;
    private final long budgetNanos;

    /**
     * Start the budget clock now.
     *
     * @param budgetMillis total latency budget in milliseconds.
     */
    public LatencyBudget(long budgetMillis) {
        this.startNanos  = System.nanoTime();
        this.budgetNanos = budgetMillis * 1_000_000L;
    }

    /** Milliseconds remaining in the budget; never negative. */
    public long remainingMillis() {
        long elapsed = System.nanoTime() - startNanos;
        return Math.max(0L, (budgetNanos - elapsed) / 1_000_000L);
    }

    /** Returns {@code true} if the budget has been fully consumed. */
    public boolean exceeded() {
        return remainingMillis() == 0;
    }

    /**
     * Returns {@code true} if at least {@code millis} milliseconds remain in the budget.
     * Use before initiating an operation that is expected to take up to {@code millis} ms.
     */
    public boolean canSpend(long millis) {
        return remainingMillis() > millis;
    }
}
