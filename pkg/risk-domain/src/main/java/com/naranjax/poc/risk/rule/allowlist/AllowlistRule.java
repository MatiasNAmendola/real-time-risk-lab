package com.naranjax.poc.risk.rule.allowlist;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.util.Set;

/**
 * Allows trusted customers to bypass all other rules when override=true.
 *
 * In the aggregation policy, a triggered AllowlistRule with override=true short-circuits
 * worst-case evaluation and returns ALLOW regardless of other rule results.
 *
 * In PoC mode, customerIds is an inline set. In production, the lookup field
 * would point to a dynamically maintained table.
 */
public final class AllowlistRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final boolean override;
    private final Set<String> customerIds;

    public AllowlistRule(String ruleName, boolean enabled, double weight,
                         boolean override, Set<String> customerIds) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.override    = override;
        this.customerIds = Set.copyOf(customerIds);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    /** Whether this rule acts as a hard override (short-circuits worst-case aggregation). */
    public boolean isOverride() { return override; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        String customerId = snapshot.customerId();
        if (customerId == null || customerId.isBlank()) {
            return RuleEvaluation.notTriggered(ruleName, "customerId is null/absent", weight, RuleAction.ALLOW);
        }

        if (customerIds.contains(customerId)) {
            return RuleEvaluation.triggered(ruleName,
                    "customerId " + customerId + " is in allowlist" + (override ? " [override]" : ""),
                    weight, RuleAction.ALLOW);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "customerId " + customerId + " is not in allowlist", weight, RuleAction.ALLOW);
    }
}
