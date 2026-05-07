package com.naranjax.distributed.shared.rules;

import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.distributed.shared.RiskRequest;

/**
 * Fires when the transaction amount exceeds the configured threshold.
 *
 * <p>Default threshold: 10_000_000 cents ($100,000.00 USD / ARS equivalent).
 * Threshold is configurable via constructor to support per-environment overrides in tests.
 */
public final class HighAmountRule implements FraudRule {

    /** Default threshold: $100,000.00 expressed in cents. */
    private static final long DEFAULT_THRESHOLD_CENTS = 10_000_000L;

    private final long thresholdCents;

    public HighAmountRule() {
        this(DEFAULT_THRESHOLD_CENTS);
    }

    public HighAmountRule(long thresholdCents) {
        this.thresholdCents = thresholdCents;
    }

    @Override
    public String name() {
        return "HighAmountRule";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public RuleEvaluation evaluate(RiskRequest request, FeatureSnapshot features) {
        boolean fired = request.amountCents() > thresholdCents;
        String reason = fired ? "amount " + request.amountCents() + " > threshold " + thresholdCents : "ok";
        return new RuleEvaluation(fired, reason, 1.0);
    }
}
