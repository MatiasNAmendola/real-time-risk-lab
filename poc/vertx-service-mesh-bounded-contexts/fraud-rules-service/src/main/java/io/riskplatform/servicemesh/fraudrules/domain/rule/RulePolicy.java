package io.riskplatform.servicemesh.fraudrules.domain.rule;

import io.riskplatform.servicemesh.shared.FraudRulesResult;
import io.riskplatform.servicemesh.shared.RiskRequest;
import java.util.ArrayList;

/** Pure fraud rules owned by fraud-rules-service. */
public final class RulePolicy {
    public FraudRulesResult evaluate(RiskRequest request) {
        var fired = new ArrayList<String>();
        int points = 0;
        if (request.amountCents() >= 500_000) {
            fired.add("high-amount-v1");
            points += 90;
        }
        if (request.newDevice() && request.amountCents() >= 100_000) {
            fired.add("new-device-high-value-v1");
            points += 45;
        }
        String recommendation = points >= 90 ? "DECLINE" : points >= 45 ? "REVIEW" : "APPROVE";
        return new FraudRulesResult(fired, recommendation, points);
    }
}
