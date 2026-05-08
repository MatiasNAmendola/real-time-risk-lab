package io.riskplatform.distributed.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound risk evaluation request – travels from controller to usecase via event bus.
 *
 * <p>{@code idempotencyKey} is optional. When present, the usecase layer will short-circuit and
 * return the previously stored decision for the same key.
 *
 * <p>{@code newDevice} signals whether the transaction originated from a device the customer
 * has not used before. Used by {@link io.riskplatform.distributed.shared.rules.NewDeviceYoungCustomerRule}.
 */
public record RiskRequest(
        @JsonProperty("transactionId")   String  transactionId,
        @JsonProperty("customerId")      String  customerId,
        @JsonProperty("amountCents")     long    amountCents,
        @JsonProperty("correlationId")   String  correlationId,
        @JsonProperty("idempotencyKey")  String  idempotencyKey,
        @JsonProperty("newDevice")       boolean newDevice
) {
    @JsonCreator
    public RiskRequest {}

    /** Backwards-compat constructor for consumers that only pass the three original fields. */
    public RiskRequest(String transactionId, String customerId, long amountCents) {
        this(transactionId, customerId, amountCents, null, null, false);
    }
}
