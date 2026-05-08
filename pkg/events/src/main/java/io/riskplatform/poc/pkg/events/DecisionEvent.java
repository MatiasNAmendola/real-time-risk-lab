package io.riskplatform.poc.pkg.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Copied from poc/no-vertx-clean-engine with re-packaged types.
 * Original: io.riskplatform.engine.domain.entity.DecisionEvent
 *
 * Decision and reason fields are kept as Strings to avoid coupling
 * to domain enums until Phase 2 aligns types.
 */
public record DecisionEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String transactionId,
        String decision,
        String reason,
        String ruleSetVersion,
        String modelVersion
) {
    public static DecisionEvent of(
            String correlationId,
            String transactionId,
            String decision,
            String reason,
            String ruleSetVersion,
            String modelVersion,
            Instant occurredAt
    ) {
        return new DecisionEvent(
                UUID.randomUUID().toString(),
                "risk.decision.evaluated",
                1,
                occurredAt,
                correlationId,
                transactionId,
                decision,
                reason,
                ruleSetVersion,
                modelVersion
        );
    }
}
