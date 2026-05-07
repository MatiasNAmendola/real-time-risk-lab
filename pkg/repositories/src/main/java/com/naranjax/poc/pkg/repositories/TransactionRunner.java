package com.naranjax.poc.pkg.repositories;

import java.util.concurrent.Callable;

/**
 * Port for executing a block of code inside a transaction.
 * Functional transaction boundary — the implementation owns commit/rollback.
 */
public interface TransactionRunner {
    <T> T run(Callable<T> action) throws Exception;

    default void runVoid(Runnable action) throws Exception {
        run(() -> { action.run(); return null; });
    }
}
