package io.riskplatform.riskdecision.cleanengine.application.common;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;

import java.util.function.Supplier;

public interface TransactionManager {
    <T> T inTransaction(ExecutionContext context, Supplier<T> work);
}
