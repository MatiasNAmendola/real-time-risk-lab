package io.riskplatform.engine.infrastructure.repository.event;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.domain.entity.DecisionEvent;
import io.riskplatform.engine.domain.repository.DecisionEventPublisher;
import io.riskplatform.engine.domain.repository.OutboxRepository;

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
