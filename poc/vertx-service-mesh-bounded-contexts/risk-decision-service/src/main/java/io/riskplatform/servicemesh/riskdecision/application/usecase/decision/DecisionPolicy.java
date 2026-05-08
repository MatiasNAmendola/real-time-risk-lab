package io.riskplatform.servicemesh.riskdecision.application.usecase.decision;

import io.riskplatform.servicemesh.shared.FraudRulesResult;
import io.riskplatform.servicemesh.shared.MlScoreResult;
import io.riskplatform.servicemesh.shared.RiskDecision;
import io.riskplatform.servicemesh.shared.RiskRequest;

public final class DecisionPolicy {
    public RiskDecision decide(RiskRequest request, FraudRulesResult rules, MlScoreResult ml) {
        if ("DECLINE".equals(rules.recommendation())) {
            return decision(request, "DECLINE", "fraud-rules:" + String.join(",", rules.firedRules()), rules, ml);
        }
        if (ml.score() > 0.72) {
            return decision(request, "DECLINE", "ml-score:" + ml.score(), rules, ml);
        }
        if ("REVIEW".equals(rules.recommendation()) || ml.score() > 0.42) {
            return decision(request, "REVIEW", "rules-or-ml-review", rules, ml);
        }
        return decision(request, "APPROVE", "bounded-contexts-approved", rules, ml);
    }

    private RiskDecision decision(RiskRequest request, String decision, String reason,
                                  FraudRulesResult rules, MlScoreResult ml) {
        return new RiskDecision(
            request.transactionId(),
            decision,
            reason,
            request.correlationId(),
            "true-service-to-service:eventbus",
            rules.recommendation(),
            ml.score());
    }
}
