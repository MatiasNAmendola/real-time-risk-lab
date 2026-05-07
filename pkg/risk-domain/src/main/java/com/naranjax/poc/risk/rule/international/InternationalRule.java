package com.naranjax.poc.risk.rule.international;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.util.Set;

/**
 * Triggers when the transaction country matches a configured restricted-country list (ISO 3166-1 alpha-2).
 * Domestic transactions (country=null) never trigger this rule.
 */
public final class InternationalRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final Set<String> restrictedCountries;

    public InternationalRule(String ruleName, boolean enabled, double weight,
                             RuleAction action, Set<String> restrictedCountries) {
        this.ruleName            = ruleName;
        this.ruleEnabled         = enabled;
        this.weight              = weight;
        this.action              = action;
        this.restrictedCountries = Set.copyOf(restrictedCountries);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        String country = snapshot.country();
        if (country == null || country.isBlank()) {
            return RuleEvaluation.notTriggered(ruleName,
                    "country is null/absent — treated as domestic transaction", weight, action);
        }

        if (restrictedCountries.contains(country.toUpperCase())) {
            return RuleEvaluation.triggered(ruleName,
                    "country " + country + " is in restricted list", weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "country " + country + " is not restricted", weight, action);
    }
}
