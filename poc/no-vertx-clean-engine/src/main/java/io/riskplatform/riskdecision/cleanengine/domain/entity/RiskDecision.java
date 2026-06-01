package io.riskplatform.riskdecision.cleanengine.domain.entity;

import java.time.Duration;

public record RiskDecision(
        TransactionId transactionId,
        Decision decision,
        String reason,
        Duration elapsed,
        DecisionTrace trace
) {
}
