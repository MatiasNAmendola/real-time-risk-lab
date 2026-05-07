package com.naranjax.poc.risk.rule.chargeback;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FieldNotFoundException;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

/**
 * Specialised threshold rule for chargeback history fields.
 *
 * Separated from the generic ThresholdRule because chargeback data comes from a
 * different store (90-day rolling window) with distinct TTL and caching semantics.
 * Supports operator >= and > against chargebackCount90d or chargebackCount30d.
 */
public final class ChargebackHistoryRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final String field;
    private final String operator;
    private final int threshold;

    public ChargebackHistoryRule(String ruleName, boolean enabled, double weight,
                                 RuleAction action, String field, String operator, int threshold) {
        if (!field.startsWith("chargebackCount")) {
            throw new IllegalArgumentException("ChargebackHistoryRule field must be chargebackCount*: " + field);
        }
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

        int actual = fieldValue.intValue();
        boolean triggered = switch (operator) {
            case ">=" -> actual >= threshold;
            case ">"  -> actual >  threshold;
            case "<=" -> actual <= threshold;
            case "<"  -> actual <  threshold;
            case "==" -> actual == threshold;
            default   -> throw new IllegalArgumentException("Unsupported operator for chargeback rule: " + operator);
        };

        if (triggered) {
            return RuleEvaluation.triggered(ruleName,
                    field + "=" + actual + " " + operator + " " + threshold, weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                field + "=" + actual + " did not match " + operator + " " + threshold, weight, action);
    }
}
