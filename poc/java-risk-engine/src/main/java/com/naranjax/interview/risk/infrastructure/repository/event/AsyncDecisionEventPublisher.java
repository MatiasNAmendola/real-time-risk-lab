package com.naranjax.interview.risk.infrastructure.repository.event;

import com.naranjax.interview.risk.domain.entity.DecisionEvent;
import com.naranjax.interview.risk.domain.repository.DecisionEventPublisher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class AsyncDecisionEventPublisher implements DecisionEventPublisher, AutoCloseable {
    private final ExecutorService executor;

    public AsyncDecisionEventPublisher(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void publish(DecisionEvent event) {
        executor.submit(() -> System.out.println("async-event=" + event));
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }
}
