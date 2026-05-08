package io.riskplatform.engine.domain.service;

import io.riskplatform.engine.domain.entity.*;
import io.riskplatform.engine.domain.rule.FraudRule;

import java.util.List;
import java.util.Optional;

public final class RuleBasedDecisionPolicy {
    private final List<FraudRule> rules;

    public RuleBasedDecisionPolicy(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Optional<RuleEvaluation> firstMatch(TransactionRiskRequest request, FeatureSnapshot features, DecisionTrace trace) {
        for (var rule : rules) {
            var evaluation = rule.evaluate(request, features);
            trace.recordRule(evaluation);
            if (evaluation.matched()) return Optional.of(evaluation);
        }
        return Optional.empty();
    }
}
