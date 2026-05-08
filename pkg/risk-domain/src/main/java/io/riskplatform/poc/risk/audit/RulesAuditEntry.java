package io.riskplatform.rules.audit;

import io.riskplatform.rules.rule.RuleAction;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record of a single rule-engine evaluation, written to the audit trail.
 *
 * The rulesVersionHash ties this decision to the exact config that was active,
 * enabling decision reproducibility weeks or months after the fact.
 */
public record RulesAuditEntry(
        Instant timestamp,
        String transactionId,
        String customerId,
        RuleAction decision,
        List<String> triggeredRules,
        String rulesVersionHash,
        String rulesVersion,
        boolean fallbackApplied,
        String overriddenBy,
        long evalMs
) {
    public static RulesAuditEntry from(
            io.riskplatform.rules.engine.FeatureSnapshot snapshot,
            io.riskplatform.rules.engine.AggregateDecision decision) {
        return new RulesAuditEntry(
                Instant.now(),
                snapshot.transactionId(),
                snapshot.customerId(),
                decision.decision(),
                decision.triggeredRuleNames(),
                decision.rulesVersionHash(),
                decision.rulesVersion(),
                decision.fallbackApplied(),
                decision.overriddenBy(),
                decision.evalMs()
        );
    }
}
