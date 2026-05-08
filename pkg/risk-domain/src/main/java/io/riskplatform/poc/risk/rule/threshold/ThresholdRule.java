package io.riskplatform.rules.rule.threshold;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.FieldNotFoundException;
import io.riskplatform.rules.rule.FraudRule;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.RuleEvaluation;

/**
 * Evaluates a numeric field from the FeatureSnapshot against a threshold using a comparison operator.
 *
 * Supported operators: {@code >}, {@code >=}, {@code <}, {@code <=}, {@code ==}, {@code !=}.
 * Throws {@link FieldNotFoundException} when the field is absent from the snapshot.
 */
public final class ThresholdRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final String field;
    private final String operator;
    private final double threshold;

    public ThresholdRule(String ruleName, boolean enabled, double weight,
                         RuleAction action, String field, String operator, double threshold) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.action      = action;
        this.field       = field;
        this.operator    = operator;
        this.threshold   = threshold;
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        Number fieldValue = snapshot.numericField(field)
                .orElseThrow(() -> new FieldNotFoundException(field));

        double value = fieldValue.doubleValue();
        boolean triggered = switch (operator) {
            case ">"  -> value >  threshold;
            case ">=" -> value >= threshold;
            case "<"  -> value <  threshold;
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            case "!=" -> value != threshold;
            default   -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };

        if (triggered) {
            return RuleEvaluation.triggered(ruleName,
                    field + " " + operator + " " + threshold + " (actual: " + value + ")",
                    weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                field + " " + value + " did not match " + operator + " " + threshold,
                weight, action);
    }
}
