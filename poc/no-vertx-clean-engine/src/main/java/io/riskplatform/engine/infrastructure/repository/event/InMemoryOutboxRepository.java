package io.riskplatform.engine.infrastructure.repository.event;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.domain.entity.DecisionEvent;
import io.riskplatform.engine.domain.repository.OutboxRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOutboxRepository implements OutboxRepository {
    private final ConcurrentHashMap<String, OutboxRecord> records = new ConcurrentHashMap<>();

    @Override
    public void append(ExecutionContext context, DecisionEvent event) {
        context.logger().info("appending event to outbox", "event_id", event.eventId(), "event_type", event.eventType());
        records.putIfAbsent(event.eventId(), new OutboxRecord(event, false));
    }

    @Override
    public List<DecisionEvent> pending(int maxItems) {
        var result = new ArrayList<DecisionEvent>();
        for (var record : records.values()) {
            if (!record.published()) result.add(record.event());
            if (result.size() >= maxItems) break;
        }
        return result;
    }

    @Override
    public void markPublished(String eventId) {
        records.computeIfPresent(eventId, (ignored, record) -> new OutboxRecord(record.event(), true));
    }

    private record OutboxRecord(DecisionEvent event, boolean published) {}
}
