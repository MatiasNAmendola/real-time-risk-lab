package io.riskplatform.distributed.repository.secrets;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves the local AWS emulator endpoint (Floci, ADR-0042) for the repository-app.
 *
 * <p>See {@code monolith.repository.FlociEndpoint} for the shared rationale. This
 * module-local copy avoids adding a cross-PoC shared dependency.
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
