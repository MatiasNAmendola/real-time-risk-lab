package io.riskplatform.distributed.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Customer feature snapshot loaded from the repository layer.
 *
 * <p>Contains both raw signals (for deterministic rule evaluation) and the derived ML score
 * (for compatibility with the pre-existing score-based policy).
 *
 * <ul>
 *   <li>{@code customerAgeDays}    – days since the customer account was created (raw signal).
 *   <li>{@code chargebackCount90d} – number of chargebacks in the last 90 days (raw signal).
 *   <li>{@code knownDevice}        – whether the device is already associated to this customer.
 *   <li>{@code riskScore}          – pre-computed ML score in [0.0, 1.0] (derived, kept for compat).
 * </ul>
 */
public record FeatureSnapshot(
        @JsonProperty("customerId")         String customerId,
        @JsonProperty("riskScore")          double riskScore,
        @JsonProperty("country")            String country,
        @JsonProperty("customerAgeDays")    int    customerAgeDays,
        @JsonProperty("chargebackCount90d") int    chargebackCount90d,
        @JsonProperty("knownDevice")        boolean knownDevice
) {
    @JsonCreator
    public FeatureSnapshot {}

    /**
     * Backwards-compat factory for existing callers that only set the three original fields.
     * Raw signal fields default to safe values (0-day-old customer, 0 chargebacks, known device).
     */
    public FeatureSnapshot(String customerId, double riskScore, String country) {
        this(customerId, riskScore, country, 0, 0, true);
    }
}
