package io.riskplatform.riskdecision.cleanengine.domain.rule;

import io.riskplatform.riskdecision.cleanengine.domain.entity.FeatureSnapshot;
import io.riskplatform.riskdecision.cleanengine.domain.entity.RuleEvaluation;
import io.riskplatform.riskdecision.cleanengine.domain.entity.TransactionRiskRequest;

public interface FraudRule {
    String name();
    RuleEvaluation evaluate(TransactionRiskRequest request, FeatureSnapshot features);
}
