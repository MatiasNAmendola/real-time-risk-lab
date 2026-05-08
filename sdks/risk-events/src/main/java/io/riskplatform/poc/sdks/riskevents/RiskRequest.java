package io.riskplatform.sdks.riskevents;

/**
 * Canonical SDK record for requesting a risk evaluation.
 *
 * <p>Field names align with the deployed Vert.x server contract:
 * see {@code poc/java-vertx-distributed/shared/.../RiskRequest.java} and
 * {@code controller-app/src/main/resources/openapi.yaml}.
 *
 * <p>{@code idempotencyKey} is optional. When present, the server short-circuits
 * and returns the previously stored decision for the same key.
 *
 * <p>{@code newDevice} signals whether the transaction originated from a device
 * the customer has not used before. Used by domain rules.
 */
public record RiskRequest(
        String  transactionId,
        String  customerId,
        long    amountCents,
        String  correlationId,
        String  idempotencyKey,
        boolean newDevice
) {
    public RiskRequest {
        if (transactionId == null || transactionId.isBlank())
            throw new IllegalArgumentException("transactionId is required");
        if (customerId == null || customerId.isBlank())
            throw new IllegalArgumentException("customerId is required");
        if (amountCents < 0)
            throw new IllegalArgumentException("amountCents must be non-negative");
    }

    /** Backwards-compat constructor for consumers that only pass the three core fields. */
    public RiskRequest(String transactionId, String customerId, long amountCents) {
        this(transactionId, customerId, amountCents, null, null, false);
    }
}
