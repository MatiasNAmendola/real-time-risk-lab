package io.riskplatform.engine.domain.context;

import io.riskplatform.engine.domain.entity.CorrelationId;
import io.riskplatform.engine.domain.port.StructuredLoggerPort;

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
