package io.riskplatform.engine.domain.rule;

import io.riskplatform.engine.domain.entity.FeatureSnapshot;
import io.riskplatform.engine.domain.entity.RuleEvaluation;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;

public interface FraudRule {
    String name();
    RuleEvaluation evaluate(TransactionRiskRequest request, FeatureSnapshot features);
}
