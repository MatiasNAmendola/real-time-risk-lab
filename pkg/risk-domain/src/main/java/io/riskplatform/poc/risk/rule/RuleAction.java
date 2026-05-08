package io.riskplatform.rules.rule;

/**
 * Actions a rule can emit. Severity order (highest = worst): DECLINE > REVIEW > FLAG > ALLOW > APPROVE.
 * Used by the aggregation policy to compute the worst-case outcome across multiple triggered rules.
 */
public enum RuleAction {
    /** Hard block — transaction must not proceed. */
    DECLINE(5),
    /** Requires manual review before proceeding. */
    REVIEW(4),
    /** Mark for monitoring; does not block the transaction. */
    FLAG(3),
    /** Explicit safe-passage override; used by allowlist rules with override=true. */
    ALLOW(2),
    /** Default pass-through when no rule fires. */
    APPROVE(1);

    private final int severity;

    RuleAction(int severity) {
        this.severity = severity;
    }

    /** Higher severity wins in worst-case aggregation. */
    public int severity() {
        return severity;
    }
}
