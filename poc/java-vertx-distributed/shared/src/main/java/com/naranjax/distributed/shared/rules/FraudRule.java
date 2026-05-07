package com.naranjax.distributed.shared.rules;

import com.naranjax.distributed.shared.FeatureSnapshot;
import com.naranjax.distributed.shared.RiskRequest;

/**
 * Sealed interface for named, versioned fraud rules that can be individually audited,
 * enabled, and disabled without touching the policy orchestrator.
 *
 * <p>Every implementation must provide:
 * <ul>
 *   <li>a stable {@link #name()} that identifies the rule in audit logs and traces;
 *   <li>a {@link #version()} that increments whenever the rule semantics change;
 *   <li>a pure {@link #evaluate(RiskRequest, FeatureSnapshot)} function with no side effects.
 * </ul>
 */
public sealed interface FraudRule permits HighAmountRule, NewDeviceYoungCustomerRule {

    /** Stable, human-readable name used in audit logs and OTel span attributes. */
    String name();

    /** Version string — increment when rule semantics change. */
    String version();

    /**
     * Evaluate whether the rule fires for the given request and its enriched feature snapshot.
     *
     * @param request  the inbound transaction request.
     * @param features the enriched customer features loaded from the repository.
     * @return a {@link RuleEvaluation} describing whether the rule triggered and why.
     */
    RuleEvaluation evaluate(RiskRequest request, FeatureSnapshot features);
}
