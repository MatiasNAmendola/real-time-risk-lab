package io.riskplatform.poc.pkg.resilience;

import java.time.Duration;
import java.util.concurrent.Callable;

/** Simple retry with fixed back-off. */
public final class Retry {

    private final int maxAttempts;
    private final Duration delay;

    public Retry(int maxAttempts, Duration delay) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    public <T> T execute(Callable<T> action) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return action.call();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) throw e;
                Thread.sleep(delay.toMillis());
            }
        }
    }
}
