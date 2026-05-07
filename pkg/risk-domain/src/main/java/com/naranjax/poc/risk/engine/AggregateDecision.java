package com.naranjax.poc.risk.engine;

import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.util.List;

/**
 * Result of evaluating all rules in a RulesConfig against a FeatureSnapshot.
 *
 * @param decision           the aggregated decision
 * @param ruleResults        all rule evaluations (triggered and not triggered)
 * @param rulesVersionHash   hash of the RulesConfig used for this evaluation
 * @param rulesVersion       semantic version string of the config
 * @param fallbackApplied    true when timeout caused early exit and fallback_decision was used
 * @param overriddenBy       name of the allowlist rule that overrode worst-case, or null
 * @param evalMs             wall-clock duration of the evaluation in milliseconds
 */
public record AggregateDecision(
        RuleAction decision,
        List<RuleEvaluation> ruleResults,
        String rulesVersionHash,
        String rulesVersion,
        boolean fallbackApplied,
        String overriddenBy,
        long evalMs
) {
    /** Convenience: names of rules that actually triggered. */
    public List<String> triggeredRuleNames() {
        return ruleResults.stream()
                .filter(RuleEvaluation::triggered)
                .map(RuleEvaluation::ruleName)
                .toList();
    }
}
