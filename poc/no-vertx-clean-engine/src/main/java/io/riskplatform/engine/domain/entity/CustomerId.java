package io.riskplatform.engine.domain.entity;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("customer id is required");
    }
}
