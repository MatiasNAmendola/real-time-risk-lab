package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.persistence;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;
import io.riskplatform.riskdecision.cleanengine.application.common.TransactionManager;

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
