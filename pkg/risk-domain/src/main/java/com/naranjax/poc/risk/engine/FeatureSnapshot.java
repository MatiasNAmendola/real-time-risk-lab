package com.naranjax.poc.risk.engine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-materialised set of signals extracted from a RiskRequest and its context.
 *
 * The engine evaluates rules against this snapshot, not against the raw HTTP request.
 * This separation allows:
 * - Feature extraction to run in parallel (IO-bound lookups done before rule evaluation).
 * - Tests to inject any combination of signals without mocks.
 * - Rule interpreters to remain pure functions with no IO.
 *
 * Fields use boxed types so that null indicates "field absent" (distinct from zero).
 * A ThresholdRule will throw FieldNotFoundException when a required field is null.
 */
public record FeatureSnapshot(
        String customerId,
        String transactionId,
        Long amountCents,
        Boolean newDevice,
        Integer customerAgeDays,
        Integer chargebackCount90d,
        Integer chargebackCount30d,
        Integer transactionCount10m,
        String merchantMcc,
        String country,
        String deviceFingerprint,
        Instant transactionTime,
        Map<String, Object> extra
) {

    /** Convenience builder for tests and feature extraction. */
    public static Builder builder() {
        return new Builder();
    }

    /** Retrieve a numeric field by name for threshold-style rules. Returns empty if absent. */
    public Optional<Number> numericField(String fieldName) {
        return switch (fieldName) {
            case "amountCents"          -> Optional.ofNullable(amountCents);
            case "customerAgeDays"      -> Optional.ofNullable(customerAgeDays);
            case "chargebackCount90d"   -> Optional.ofNullable(chargebackCount90d);
            case "chargebackCount30d"   -> Optional.ofNullable(chargebackCount30d);
            case "transactionCount10m"  -> Optional.ofNullable(transactionCount10m);
            default -> {
                if (extra != null && extra.get(fieldName) instanceof Number n) {
                    yield Optional.of(n);
                }
                yield Optional.empty();
            }
        };
    }

    /** Retrieve a boolean field by name for combination sub-rules. */
    public Optional<Boolean> booleanField(String fieldName) {
        return switch (fieldName) {
            case "newDevice" -> Optional.ofNullable(newDevice);
            default -> {
                if (extra != null && extra.get(fieldName) instanceof Boolean b) {
                    yield Optional.of(b);
                }
                yield Optional.empty();
            }
        };
    }

    /** Day of week derived from transactionTime UTC. */
    public DayOfWeek transactionDayOfWeek() {
        if (transactionTime == null) return null;
        return transactionTime.atZone(java.time.ZoneOffset.UTC).getDayOfWeek();
    }

    /** Hour of day (UTC) derived from transactionTime. */
    public int transactionHourUtc() {
        if (transactionTime == null) return -1;
        return transactionTime.atZone(java.time.ZoneOffset.UTC).getHour();
    }

    public static final class Builder {
        private String customerId;
        private String transactionId;
        private Long amountCents;
        private Boolean newDevice;
        private Integer customerAgeDays;
        private Integer chargebackCount90d;
        private Integer chargebackCount30d;
        private Integer transactionCount10m;
        private String merchantMcc;
        private String country;
        private String deviceFingerprint;
        private Instant transactionTime;
        private Map<String, Object> extra;

        public Builder customerId(String v)            { customerId = v; return this; }
        public Builder transactionId(String v)         { transactionId = v; return this; }
        public Builder amountCents(long v)             { amountCents = v; return this; }
        public Builder newDevice(boolean v)            { newDevice = v; return this; }
        public Builder customerAgeDays(int v)          { customerAgeDays = v; return this; }
        public Builder chargebackCount90d(int v)       { chargebackCount90d = v; return this; }
        public Builder chargebackCount30d(int v)       { chargebackCount30d = v; return this; }
        public Builder transactionCount10m(int v)      { transactionCount10m = v; return this; }
        public Builder merchantMcc(String v)           { merchantMcc = v; return this; }
        public Builder country(String v)               { country = v; return this; }
        public Builder deviceFingerprint(String v)     { deviceFingerprint = v; return this; }
        public Builder transactionTime(Instant v)      { transactionTime = v; return this; }
        public Builder extra(Map<String, Object> v)    { extra = v; return this; }

        public FeatureSnapshot build() {
            return new FeatureSnapshot(customerId, transactionId, amountCents, newDevice,
                    customerAgeDays, chargebackCount90d, chargebackCount30d,
                    transactionCount10m, merchantMcc, country, deviceFingerprint,
                    transactionTime, extra);
        }
    }
}
