package io.riskplatform.poc.pkg.resilience;

import java.util.concurrent.Semaphore;
import java.util.concurrent.Callable;

/** Limits concurrent access to a resource via a counting semaphore. */
public final class Bulkhead {

    private final Semaphore semaphore;
    private final String name;

    public Bulkhead(String name, int maxConcurrent) {
        this.name = name;
        this.semaphore = new Semaphore(maxConcurrent, true);
    }

    public <T> T execute(Callable<T> action) throws Exception {
        if (!semaphore.tryAcquire()) {
            throw new BulkheadFullException(name);
        }
        try {
            return action.call();
        } finally {
            semaphore.release();
        }
    }

    public static final class BulkheadFullException extends RuntimeException {
        public BulkheadFullException(String bulkheadName) {
            super("Bulkhead '" + bulkheadName + "' is at capacity");
        }
    }
}
