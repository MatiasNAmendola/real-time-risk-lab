package com.naranjax.poc.risk.rule;

import com.naranjax.poc.risk.engine.FeatureSnapshot;

/**
 * Interface for all fraud rule interpreters.
 *
 * Each implementation corresponds to one rule type in the YAML configuration.
 * Implementations are pre-compiled at config load time, not on every evaluation,
 * so this method is called in a tight loop and must allocate as little as possible.
 *
 * Supported implementations: ThresholdRule, CombinationRule, VelocityRule,
 * ChargebackHistoryRule, InternationalRule, TimeOfDayRule, MerchantCategoryRule,
 * AllowlistRule, DeviceFingerprintRule.
 */
public interface FraudRule {

    /** Unique name matching rules.yaml. */
    String name();

    /** Evaluate the rule against the pre-materialised feature snapshot. */
    RuleEvaluation evaluate(FeatureSnapshot snapshot);

    /** Whether this rule is enabled. Disabled rules are never evaluated. */
    boolean enabled();
}
