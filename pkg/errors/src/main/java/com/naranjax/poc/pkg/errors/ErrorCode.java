package com.naranjax.poc.pkg.errors;

/** Canonical error codes shared across services. */
public enum ErrorCode {
    // Generic
    UNKNOWN,
    VALIDATION_ERROR,
    NOT_FOUND,
    ALREADY_EXISTS,
    UNAUTHORIZED,
    FORBIDDEN,
    // Domain
    BUSINESS_RULE_VIOLATION,
    IDEMPOTENCY_CONFLICT,
    CIRCUIT_OPEN,
    TIMEOUT,
    RATE_LIMITED,
    DOWNSTREAM_UNAVAILABLE
}
