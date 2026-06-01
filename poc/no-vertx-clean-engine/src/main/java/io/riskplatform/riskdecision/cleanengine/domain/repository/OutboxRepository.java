package io.riskplatform.riskdecision.cleanengine.domain.repository;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;
import io.riskplatform.riskdecision.cleanengine.domain.entity.DecisionEvent;

import java.util.List;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface OutboxRepository {
    void append(ExecutionContext context, DecisionEvent event);
    List<DecisionEvent> pending(int maxItems);
    void markPublished(String eventId);
}
