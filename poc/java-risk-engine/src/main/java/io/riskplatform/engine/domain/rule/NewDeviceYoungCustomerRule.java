package io.riskplatform.engine.domain.rule;

import io.riskplatform.engine.domain.entity.*;

public final class NewDeviceYoungCustomerRule implements FraudRule {
    @Override public String name() { return "new-device-young-customer-v1"; }

    @Override
    public RuleEvaluation evaluate(TransactionRiskRequest request, FeatureSnapshot features) {
        if (request.newDevice() && features.customerAgeDays() < 30) {
            return new RuleEvaluation(name(), true, Decision.REVIEW, "new-device-young-customer");
        }
        return RuleEvaluation.noMatch(name());
    }
}
