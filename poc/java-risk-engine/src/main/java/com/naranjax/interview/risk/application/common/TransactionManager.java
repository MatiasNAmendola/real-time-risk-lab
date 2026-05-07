package com.naranjax.interview.risk.application.common;

import com.naranjax.interview.risk.domain.context.ExecutionContext;

import java.util.function.Supplier;

public interface TransactionManager {
    <T> T inTransaction(ExecutionContext context, Supplier<T> work);
}
