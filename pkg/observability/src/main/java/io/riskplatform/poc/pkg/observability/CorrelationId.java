package io.riskplatform.poc.pkg.observability;

/**
 * Copied from poc/no-vertx-clean-engine, re-packaged.
 * Original: io.riskplatform.engine.domain.entity.CorrelationId
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
