package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.entity.DecisionEvent;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionEventPublisher {
    void publish(DecisionEvent event);
}
