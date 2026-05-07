package com.naranjax.interview.risk.infrastructure.repository.persistence;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.application.common.TransactionManager;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class InMemoryTransactionManager implements TransactionManager {
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public <T> T inTransaction(ExecutionContext context, Supplier<T> work) {
        context.logger().info("begin transaction");
        lock.lock();
        try {
            var result = work.get();
            context.logger().info("commit transaction");
            return result;
        } catch (RuntimeException ex) {
            context.logger().error(ex, "rollback transaction");
            throw ex;
        } finally {
            lock.unlock();
        }
    }
}
