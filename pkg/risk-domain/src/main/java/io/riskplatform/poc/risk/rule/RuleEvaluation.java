package io.riskplatform.rules.rule;

/**
 * Result of evaluating a single rule against a FeatureSnapshot.
 *
 * @param triggered  true when the rule condition matched
 * @param reason     human-readable explanation (always populated)
 * @param weight     copied from the rule configuration for audit purposes
 * @param action     the action the rule would take (meaningful only when triggered=true)
 * @param ruleName   name of the rule that produced this result
 */
public record RuleEvaluation(
        boolean triggered,
        String reason,
        double weight,
        RuleAction action,
        String ruleName
) {
    public static RuleEvaluation triggered(String ruleName, String reason, double weight, RuleAction action) {
        return new RuleEvaluation(true, reason, weight, action, ruleName);
    }

    public static RuleEvaluation notTriggered(String ruleName, String reason, double weight, RuleAction action) {
        return new RuleEvaluation(false, reason, weight, action, ruleName);
    }
}
