package io.riskplatform.engine.domain.rule;

import io.riskplatform.engine.domain.entity.*;

public final class HighAmountRule implements FraudRule {
    @Override public String name() { return "high-amount-v1"; }

    @Override
    public RuleEvaluation evaluate(TransactionRiskRequest request, FeatureSnapshot features) {
        if (request.amount().greaterThanOrEqual(50_000)) {
            return new RuleEvaluation(name(), true, Decision.REVIEW, "amount-over-threshold");
        }
        return RuleEvaluation.noMatch(name());
    }
}
