package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.event;

import io.riskplatform.riskdecision.cleanengine.domain.entity.DecisionEvent;
import io.riskplatform.riskdecision.cleanengine.domain.repository.DecisionEventPublisher;

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
