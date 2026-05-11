package io.riskplatform.distributed.consumer.aws;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves the local AWS emulator endpoint (Floci, ADR-0042) for the consumer-app.
 *
 * <p>Fallback chain: {@code FLOCI_ENDPOINT} → {@code AWS_ENDPOINT_URL} → legacy
 * per-service env vars (e.g. {@code AWS_ENDPOINT_URL_S3}) → empty (real AWS).
 */
public final class FlociEndpoint {

    private FlociEndpoint() {}

    public static Optional<URI> resolve(String... legacyKeys) {
        String value = firstNonBlank("FLOCI_ENDPOINT", "AWS_ENDPOINT_URL");
        if (value == null) {
            for (String k : legacyKeys) {
                String v = System.getenv(k);
                if (v != null && !v.isBlank()) { value = v; break; }
            }
        }
        return value == null ? Optional.empty() : Optional.of(URI.create(value));
    }

    private static String firstNonBlank(String... keys) {
        for (String k : keys) {
            String v = System.getenv(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
