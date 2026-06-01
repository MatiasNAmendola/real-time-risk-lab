package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.event;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;
import io.riskplatform.riskdecision.cleanengine.domain.entity.DecisionEvent;
import io.riskplatform.riskdecision.cleanengine.domain.repository.DecisionEventPublisher;
import io.riskplatform.riskdecision.cleanengine.domain.repository.OutboxRepository;

public final class OutboxDecisionEventPublisher implements DecisionEventPublisher {
    private final OutboxRepository outbox;
    private final ExecutionContext context;

    public OutboxDecisionEventPublisher(OutboxRepository outbox, ExecutionContext context) {
        this.outbox = outbox;
        this.context = context;
    }

    @Override
    public void publish(DecisionEvent event) {
        outbox.append(context, event);
    }
}
