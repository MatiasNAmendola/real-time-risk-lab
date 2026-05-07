package com.naranjax.distributed.shared.rules;

import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.distributed.shared.RiskRequest;

/**
 * Fires when the transaction comes from a new (unrecognised) device AND the customer account
 * is younger than {@value #YOUNG_THRESHOLD_DAYS} days.
 *
 * <p>This rule demonstrates the feature-enrichment pattern: it combines a signal from the
 * inbound request ({@code newDevice}) with a signal loaded from the feature store
 * ({@code customerAgeDays}).  Neither signal alone is sufficient — the combination is what
 * elevates the risk.
 */
public final class NewDeviceYoungCustomerRule implements FraudRule {

    /** Accounts younger than this number of days are considered "young". */
    private static final int YOUNG_THRESHOLD_DAYS = 30;

    @Override
    public String name() {
        return "NewDeviceYoungCustomerRule";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public RuleEvaluation evaluate(RiskRequest request, FeatureSnapshot features) {
        boolean fired = request.newDevice() && features.customerAgeDays() < YOUNG_THRESHOLD_DAYS;
        String reason = fired
                ? "new device + customer age " + features.customerAgeDays() + " days < " + YOUNG_THRESHOLD_DAYS
                : "ok";
        return new RuleEvaluation(fired, reason, 0.7);
    }
}
