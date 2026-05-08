package io.riskplatform.poc.pkg.events;

import java.util.List;

/**
 * Outbound port for the transactional outbox pattern.
 * Copied from poc/no-vertx-clean-engine, re-packaged and decoupled from ExecutionContext.
 * Original: io.riskplatform.engine.domain.repository.OutboxRepository
 */
public interface OutboxRepository {
    void append(String correlationId, DecisionEvent event);
    List<DecisionEvent> pending(int maxItems);
    void markPublished(String eventId);
}
