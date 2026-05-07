package com.naranjax.poc.risk.rule.combination;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.util.List;

/**
 * Combines N inline sub-rules with AND (requireAll=true) or OR (requireAll=false) semantics.
 *
 * An empty sub-rule list never triggers regardless of requireAll.
 */
public final class CombinationRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final boolean requireAll;
    private final List<SubRule> subRules;

    public CombinationRule(String ruleName, boolean enabled, double weight,
                           RuleAction action, boolean requireAll, List<SubRule> subRules) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.action      = action;
        this.requireAll  = requireAll;
        this.subRules    = List.copyOf(subRules);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        if (subRules.isEmpty()) {
            return RuleEvaluation.notTriggered(ruleName, "no sub-rules configured", weight, action);
        }

        boolean triggered;
        if (requireAll) {
            triggered = subRules.stream().allMatch(sr -> sr.evaluate(snapshot));
        } else {
            triggered = subRules.stream().anyMatch(sr -> sr.evaluate(snapshot));
        }

        String mode = requireAll ? "AND" : "OR";
        if (triggered) {
            return RuleEvaluation.triggered(ruleName,
                    "combination[" + mode + "] all conditions met", weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "combination[" + mode + "] conditions not met", weight, action);
    }
}
