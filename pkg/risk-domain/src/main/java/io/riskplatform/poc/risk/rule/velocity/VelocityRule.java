package io.riskplatform.rules.rule.velocity;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.FraudRule;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.RuleEvaluation;

/**
 * Evaluates transaction velocity against a pre-computed count in the FeatureSnapshot.
 *
 * The window management (sliding window, Redis sorted set, etc.) is handled by the
 * FeatureExtractor before this rule runs. The rule interpreter only reads the
 * pre-computed count field from the snapshot.
 *
 * Triggers when: transactionCount10m >= count threshold.
 */
public final class VelocityRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final int maxCount;
    private final int windowMinutes;
    private final String groupBy;

    public VelocityRule(String ruleName, boolean enabled, double weight, RuleAction action,
                        int maxCount, int windowMinutes, String groupBy) {
        this.ruleName      = ruleName;
        this.ruleEnabled   = enabled;
        this.weight        = weight;
        this.action        = action;
        this.maxCount      = maxCount;
        this.windowMinutes = windowMinutes;
        this.groupBy       = groupBy;
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        // The pre-computed count field is transactionCount10m.
        // For configurable windows, the FeatureExtractor maps windowMinutes to the correct field.
        Number count = snapshot.numericField("transactionCount10m").orElse(0);
        int actual = count.intValue();

        if (actual >= maxCount) {
            return RuleEvaluation.triggered(ruleName,
                    "velocity: " + actual + " transactions in " + windowMinutes + "m >= limit " + maxCount
                            + " (groupBy=" + groupBy + ")",
                    weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "velocity: " + actual + " transactions in " + windowMinutes + "m < limit " + maxCount,
                weight, action);
    }
}
