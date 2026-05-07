package com.naranjax.poc.pkg.observability;

/**
 * Copied from poc/java-risk-engine, re-packaged.
 * Original: com.naranjax.interview.risk.domain.entity.CorrelationId
 */
public record CorrelationId(String value) {
    public CorrelationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("correlation id is required");
        }
    }

    public static CorrelationId of(String value) {
        return new CorrelationId(value);
    }
}
