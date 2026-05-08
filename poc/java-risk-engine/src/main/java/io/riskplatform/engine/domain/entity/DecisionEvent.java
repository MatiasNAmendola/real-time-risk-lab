package io.riskplatform.engine.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record DecisionEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String transactionId,
        Decision decision,
        String reason,
        String ruleSetVersion,
        String modelVersion
) {
    public static DecisionEvent from(TransactionRiskRequest request, RiskDecision decision, Instant occurredAt) {
        return new DecisionEvent(
                UUID.randomUUID().toString(),
                "risk.decision.evaluated",
                1,
                occurredAt,
                request.correlationId().value(),
                request.transactionId().value(),
                decision.decision(),
                decision.reason(),
                decision.trace().ruleSetVersion(),
                decision.trace().modelVersion()
        );
    }
}
