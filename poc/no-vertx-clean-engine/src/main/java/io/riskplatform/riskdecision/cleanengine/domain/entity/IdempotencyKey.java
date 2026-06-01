package io.riskplatform.riskdecision.cleanengine.domain.entity;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("idempotency key is required");
    }
}
