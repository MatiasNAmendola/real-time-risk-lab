package com.naranjax.interview.risk.domain.context;

import com.naranjax.interview.risk.domain.entity.CorrelationId;
import com.naranjax.interview.risk.domain.port.StructuredLoggerPort;

/**
 * Carrier for cross-cutting request-scoped data (correlation ID, structured logger).
 * Lives in domain.context so both domain and application layers can reference it
 * without introducing an upward dependency on application.common.
 */
public record ExecutionContext(CorrelationId correlationId, StructuredLoggerPort logger) {
    public ExecutionContext with(String key, Object value) {
        return new ExecutionContext(correlationId, logger.with(key, value));
    }
}
