package com.naranjax.poc.risk.rule.device;

import com.naranjax.poc.risk.engine.FeatureSnapshot;
import com.naranjax.poc.risk.rule.FraudRule;
import com.naranjax.poc.risk.rule.RuleAction;
import com.naranjax.poc.risk.rule.RuleEvaluation;

import java.util.Set;

/**
 * Triggers when the device fingerprint appears in a denylist.
 *
 * In PoC mode, denyList is an inline set.
 * In production, the lookup field points to a dynamically maintained fraud device table.
 */
public final class DeviceFingerprintRule implements FraudRule {

    private final String ruleName;
    private final boolean ruleEnabled;
    private final double weight;
    private final RuleAction action;
    private final Set<String> denyList;

    public DeviceFingerprintRule(String ruleName, boolean enabled, double weight,
                                 RuleAction action, Set<String> denyList) {
        this.ruleName    = ruleName;
        this.ruleEnabled = enabled;
        this.weight      = weight;
        this.action      = action;
        this.denyList    = Set.copyOf(denyList);
    }

    @Override
    public String name() { return ruleName; }

    @Override
    public boolean enabled() { return ruleEnabled; }

    @Override
    public RuleEvaluation evaluate(FeatureSnapshot snapshot) {
        String fingerprint = snapshot.deviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            return RuleEvaluation.notTriggered(ruleName, "deviceFingerprint is null/absent", weight, action);
        }

        if (denyList.contains(fingerprint)) {
            return RuleEvaluation.triggered(ruleName,
                    "deviceFingerprint " + fingerprint + " is in denylist", weight, action);
        }
        return RuleEvaluation.notTriggered(ruleName,
                "deviceFingerprint " + fingerprint + " is not in denylist", weight, action);
    }
}
