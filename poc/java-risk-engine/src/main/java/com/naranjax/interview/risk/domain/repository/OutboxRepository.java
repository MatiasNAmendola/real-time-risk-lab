package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.domain.entity.DecisionEvent;

import java.util.List;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface OutboxRepository {
    void append(ExecutionContext context, DecisionEvent event);
    List<DecisionEvent> pending(int maxItems);
    void markPublished(String eventId);
}
