package com.naranjax.poc.pkg.observability;

import org.slf4j.MDC;

import java.util.function.Supplier;

/** Helpers for structured logging via SLF4J MDC. */
public final class MdcUtils {

    private MdcUtils() {}

    public static void put(String key, String value) {
        MDC.put(key, value);
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static void clear() {
        MDC.clear();
    }

    /**
     * Runs the supplier with the given MDC key/value set, then removes it.
     */
    public static <T> T withKey(String key, String value, Supplier<T> action) {
        MDC.put(key, value);
        try {
            return action.get();
        } finally {
            MDC.remove(key);
        }
    }

    public static void withCorrelationId(CorrelationId id, Runnable action) {
        MDC.put("correlationId", id.value());
        try {
            action.run();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
