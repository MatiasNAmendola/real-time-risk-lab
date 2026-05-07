package com.naranjax.poc.pkg.observability;

/**
 * Thin helper to attach semantic span attributes in a no-framework style.
 * Real instrumentation is expected to come from the OTel Java agent at runtime.
 */
public final class OtelSpan {

    private OtelSpan() {}

    /** Semantic attribute key for service name. */
    public static final String ATTR_SERVICE = "service.name";
    /** Semantic attribute key for correlation id. */
    public static final String ATTR_CORRELATION_ID = "naranjax.correlation_id";
    /** Semantic attribute key for transaction id. */
    public static final String ATTR_TRANSACTION_ID = "naranjax.transaction_id";

    /**
     * Returns a formatted span name following the convention:
     * {@code <service>/<operation>}
     */
    public static String name(String service, String operation) {
        return service + "/" + operation;
    }
}
