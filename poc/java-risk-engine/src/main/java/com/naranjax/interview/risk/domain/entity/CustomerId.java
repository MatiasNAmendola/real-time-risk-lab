package com.naranjax.interview.risk.domain.entity;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("customer id is required");
    }
}
