package com.naranjax.poc.pkg.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic transport envelope for any domain event.
 *
 * @param <T> the payload type
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        T payload
) {
    public static <T> EventEnvelope<T> wrap(String eventType, int version, String correlationId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                version,
                Instant.now(),
                correlationId,
                payload
        );
    }
}
