package io.riskplatform.rules.rule.mcc;

import io.riskplatform.rules.engine.FeatureSnapshot;
import io.riskplatform.rules.rule.FraudRule;
import io.riskplatform.rules.rule.RuleAction;
import io.riskplatform.rules.rule.RuleEvaluation;

import java.util.Set;

/**
 * Triggers when the merchant MCC (4-digit ISO 18245 code) appears in a configured high-risk list.
 * Transactions without an MCC (null) never trigger this rule.
 */
public final class MerchantCategoryRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final Set<String> mccCodes;

    public MerchantCategoryRule(String ruleName, boolean enabled, double weight,
                                RuleAction action, Set<String> mccCodes) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.action      = action;
        this.mccCodes    = Set.copyOf(mccCodes);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        String mcc = snapshot.merchantMcc();
        if (mcc == null || mcc.isBlank()) {
            return RuleEvaluation.notTriggered(ruleName, "merchantMcc is null/absent", weight, action);
        }

        if (mccCodes.contains(mcc)) {
            return RuleEvaluation.triggered(ruleName,
                    "merchantMcc " + mcc + " is in high-risk list", weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "merchantMcc " + mcc + " is not in high-risk list", weight, action);
    }
}
