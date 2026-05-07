package com.naranjax.poc.risk.rule.combination;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FieldNotFoundException;

/**
 * An inline sub-rule used within a CombinationRule.
 * Supports two evaluation modes: boolean equality and numeric threshold.
 */
public sealed interface SubRule permits SubRule.BooleanSubRule, SubRule.ThresholdSubRule {

    boolean evaluate(FeatureSnapshot snapshot);

    record BooleanSubRule(String field, boolean expectedValue) implements SubRule {
        @Override
        public boolean evaluate(FeatureSnapshot snapshot) {
            Boolean actual = snapshot.booleanField(field)
                    .orElseThrow(() -> new FieldNotFoundException(field));
            return actual == expectedValue;
        }
    }

    record ThresholdSubRule(String field, String operator, double value) implements SubRule {
        @Override
        public boolean evaluate(FeatureSnapshot snapshot) {
            Number n = snapshot.numericField(field)
                    .orElseThrow(() -> new FieldNotFoundException(field));
            double actual = n.doubleValue();
            return switch (operator) {
                case ">"  -> actual >  value;
                case ">=" -> actual >= value;
                case "<"  -> actual <  value;
                case "<=" -> actual <= value;
                case "==" -> actual == value;
                case "!=" -> actual != value;
                default   -> throw new IllegalArgumentException("Unknown operator: " + operator);
            };
        }
    }
}
