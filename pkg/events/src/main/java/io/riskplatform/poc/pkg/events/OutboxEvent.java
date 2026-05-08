package io.riskplatform.poc.pkg.events;

import java.time.Instant;

/**
 * Durable outbox row. Persisted before publishing; relay marks it published.
 */
public record OutboxEvent(
        String eventId,
        String eventType,
        int eventVersion,
        String correlationId,
        String payloadJson,
        Instant createdAt,
        boolean published
) {
    public OutboxEvent markPublished() {
        return new OutboxEvent(eventId, eventType, eventVersion, correlationId, payloadJson, createdAt, true);
    }
}
