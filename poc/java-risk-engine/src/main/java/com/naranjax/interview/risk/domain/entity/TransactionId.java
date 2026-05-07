package com.naranjax.interview.risk.domain.entity;

public record TransactionId(String value) {
    public TransactionId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("transaction id is required");
    }
}
