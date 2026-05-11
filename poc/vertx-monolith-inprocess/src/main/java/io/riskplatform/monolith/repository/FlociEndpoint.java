package io.riskplatform.monolith.repository;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves the local AWS emulator endpoint (Floci, ADR-0042).
 *
 * <p>Fallback chain:
 * <ol>
 *   <li>{@code FLOCI_ENDPOINT}      — unified endpoint introduced by ADR-0042.</li>
 *   <li>{@code AWS_ENDPOINT_URL}    — standard AWS SDK v2 env var (also set by Floci).</li>
 *   <li>Legacy per-service env vars (passed as {@code legacyKeys}) for backwards
 *       compatibility during the migration window.</li>
 *   <li>Otherwise empty — AWS SDK falls back to the real AWS endpoint resolver.</li>
 * </ol>
 *
 * <p>Callers wire the result via {@code builder.applyMutation(b -> resolve(...).ifPresent(b::endpointOverride))}
 * or by passing the {@link URI} to {@code endpointOverride(...)} directly.
 */
public final class FlociEndpoint {

    private FlociEndpoint() {}

    /**
     * Returns the AWS emulator endpoint URI, if configured.
     *
     * @param legacyKeys legacy per-service env var names to check after the unified ones
     *                   (e.g. {@code "AWS_ENDPOINT_URL_S3"}). May be empty.
     */
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
