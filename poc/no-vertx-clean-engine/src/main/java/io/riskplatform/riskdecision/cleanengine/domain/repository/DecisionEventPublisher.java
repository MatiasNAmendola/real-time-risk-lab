package io.riskplatform.riskdecision.cleanengine.domain.repository;

import io.riskplatform.riskdecision.cleanengine.domain.entity.DecisionEvent;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionEventPublisher {
    void publish(DecisionEvent event);
}
