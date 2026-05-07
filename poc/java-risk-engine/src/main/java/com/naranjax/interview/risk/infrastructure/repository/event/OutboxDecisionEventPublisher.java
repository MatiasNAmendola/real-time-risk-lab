package com.naranjax.interview.risk.infrastructure.repository.event;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.domain.entity.DecisionEvent;
import com.naranjax.interview.risk.domain.repository.DecisionEventPublisher;
import com.naranjax.interview.risk.domain.repository.OutboxRepository;

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
