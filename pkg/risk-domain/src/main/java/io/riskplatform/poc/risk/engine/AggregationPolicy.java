package io.riskplatform.rules.engine;

import io.riskplatform.rules.rule.AllowlistOverrideable;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.RuleEvaluation;
import io.riskplatform.rules.rule.allowlist.AllowlistRule;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements the worst_case_with_allowlist_override aggregation policy.
 *
 * Rules:
 * 1. If any triggered AllowlistRule has override=true, return ALLOW immediately.
 * 2. Otherwise, return the action with the highest severity (DECLINE > REVIEW > FLAG > ALLOW).
 * 3. If no rules triggered, return APPROVE (default pass-through).
 */
public final class AggregationPolicy {

    /**
     * Aggregates all rule evaluation results into a single action.
     *
     * @param results     all evaluations (including not-triggered rules)
     * @param compiledRules compiled rules in same order as results (for allowlist override check)
     * @param overriddenByRef output parameter — set to the allowlist rule name if override triggered
     * @return aggregated RuleAction
     */
    public RuleAction aggregate(List<RuleEvaluation> results,
                                List<io.riskplatform.rules.rule.FraudRule> compiledRules,
                                AtomicReference<String> overriddenByRef) {
        // Pass 1: check for allowlist override
        for (int i = 0; i < results.size(); i++) {
            RuleEvaluation eval = results.get(i);
            if (eval.triggered() && i < compiledRules.size()) {
                io.riskplatform.rules.rule.FraudRule rule = compiledRules.get(i);
                if (rule instanceof AllowlistRule al && al.isOverride()) {
                    overriddenByRef.set(eval.ruleName());
                    return RuleAction.ALLOW;
                }
            }
        }

        // Pass 2: worst-case across all triggered rules
        RuleAction worst = null;
        for (RuleEvaluation eval : results) {
            if (!eval.triggered()) continue;
            if (worst == null || eval.action().severity() > worst.severity()) {
                worst = eval.action();
            }
        }

        return worst != null ? worst : RuleAction.APPROVE;
    }
}
