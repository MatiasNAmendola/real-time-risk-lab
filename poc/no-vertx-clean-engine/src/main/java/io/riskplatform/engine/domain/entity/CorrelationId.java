package io.riskplatform.engine.domain.entity;

public record CorrelationId(String value) {
    public CorrelationId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("correlation id is required");
    }
}
