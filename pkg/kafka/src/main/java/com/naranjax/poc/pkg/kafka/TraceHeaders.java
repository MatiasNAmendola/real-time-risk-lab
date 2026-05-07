package com.naranjax.poc.pkg.kafka;

/** Standard Kafka header names for distributed tracing propagation. */
public final class TraceHeaders {

    private TraceHeaders() {}

    public static final String CORRELATION_ID  = "x-correlation-id";
    public static final String TRACEPARENT     = "traceparent";
    public static final String TRACESTATE      = "tracestate";
    public static final String IDEMPOTENCY_KEY = "x-idempotency-key";
    public static final String SOURCE_SERVICE  = "x-source-service";
}
