package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.entity.DecisionEvent;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionEventPublisher {
    void publish(DecisionEvent event);
}
