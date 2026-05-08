package io.riskplatform.engine.application.common;

import io.riskplatform.engine.domain.context.ExecutionContext;

import java.util.function.Supplier;

public interface TransactionManager {
    <T> T inTransaction(ExecutionContext context, Supplier<T> work);
}
