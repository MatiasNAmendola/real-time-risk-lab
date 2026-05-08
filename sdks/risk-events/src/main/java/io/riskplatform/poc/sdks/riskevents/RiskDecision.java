package io.riskplatform.sdks.riskevents;

import java.time.Duration;

/**
 * SDK record representing the outcome of a risk evaluation.
 * Re-packaged from poc/no-vertx-clean-engine RiskDecision domain entity.
 * Uses String for decision to avoid coupling external consumers to internal enums.
 */
public record RiskDecision(
        String transactionId,
        String decision,
        String reason,
        Duration elapsed
) {
    public boolean isApproved()  { return "APPROVE".equalsIgnoreCase(decision); }
    public boolean isDeclined()  { return "DECLINE".equalsIgnoreCase(decision); }
    public boolean requiresReview() { return "REVIEW".equalsIgnoreCase(decision); }
}
