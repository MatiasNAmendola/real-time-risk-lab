package com.naranjax.poc.sdks.riskevents;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical SDK event emitted after every risk evaluation.
 * Consumers subscribe to this event type via Kafka or the event bus.
 */
public record DecisionEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String transactionId,
        String decision,
        String reason
) {
    public static final String EVENT_TYPE = "risk.decision.evaluated";
    public static final int CURRENT_VERSION = 1;

    public static DecisionEvent from(RiskDecision decision, String correlationId, Instant occurredAt) {
        return new DecisionEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                CURRENT_VERSION,
                occurredAt,
                correlationId,
                decision.transactionId(),
                decision.decision(),
                decision.reason()
        );
    }
}
