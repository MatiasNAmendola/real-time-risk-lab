package com.naranjax.interview.risk.domain.rule;

import com.naranjax.interview.risk.domain.entity.FeatureSnapshot;
import com.naranjax.interview.risk.domain.entity.RuleEvaluation;
import com.naranjax.interview.risk.domain.entity.TransactionRiskRequest;

public interface FraudRule {
    String name();
    RuleEvaluation evaluate(TransactionRiskRequest request, FeatureSnapshot features);
}
