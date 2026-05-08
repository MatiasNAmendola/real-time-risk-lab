package io.riskplatform.poc.pkg.integrationaudit;

import java.time.Instant;

/**
 * Immutable audit record for an outbound integration call.
 * Persisted by the infrastructure layer for observability and replay.
 */
public record IntegrationRequestLog(
        String logId,
        String correlationId,
        String targetService,
        String operation,
        int httpStatusCode,
        long durationMs,
        boolean success,
        String errorMessage,
        Instant requestedAt
) {
    /** Convenience factory for successful calls. */
    public static IntegrationRequestLog success(
            String logId, String correlationId, String targetService,
            String operation, int status, long durationMs, Instant requestedAt) {
        return new IntegrationRequestLog(logId, correlationId, targetService,
                operation, status, durationMs, true, null, requestedAt);
    }

    /** Convenience factory for failed calls. */
    public static IntegrationRequestLog failure(
            String logId, String correlationId, String targetService,
            String operation, int status, long durationMs,
            String errorMessage, Instant requestedAt) {
        return new IntegrationRequestLog(logId, correlationId, targetService,
                operation, status, durationMs, false, errorMessage, requestedAt);
    }
}
