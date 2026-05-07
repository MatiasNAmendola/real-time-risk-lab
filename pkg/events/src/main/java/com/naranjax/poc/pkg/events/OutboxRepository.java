package com.naranjax.poc.pkg.events;

import java.util.List;

/**
 * Outbound port for the transactional outbox pattern.
 * Copied from poc/java-risk-engine, re-packaged and decoupled from ExecutionContext.
 * Original: com.naranjax.interview.risk.domain.repository.OutboxRepository
 */
public interface OutboxRepository {
    void append(String correlationId, DecisionEvent event);
    List<DecisionEvent> pending(int maxItems);
    void markPublished(String eventId);
}
