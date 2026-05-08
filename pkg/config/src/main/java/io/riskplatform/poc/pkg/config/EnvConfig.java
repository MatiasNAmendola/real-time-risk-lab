package io.riskplatform.poc.pkg.config;

import java.util.Optional;

/**
 * Utility for reading typed configuration from environment variables with
 * optional defaults. Intentionally free of any DI framework.
 */
public final class EnvConfig {

    private EnvConfig() {}

    public static String require(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + key);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    public static Optional<String> optional(String key) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? Optional.of(value) : Optional.empty();
    }

    public static int getInt(String key, int defaultValue) {
        return optional(key).map(Integer::parseInt).orElse(defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return optional(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }
}
