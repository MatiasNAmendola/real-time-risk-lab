package io.riskplatform.engine.domain.entity;

public record Money(long cents, String currency) {
    public Money {
        if (cents < 0) throw new IllegalArgumentException("amount cannot be negative");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    public boolean greaterThanOrEqual(long thresholdInCents) {
        return cents >= thresholdInCents;
    }
}
