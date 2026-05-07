package com.naranjax.interview.risk.infrastructure.repository.event;

import com.naranjax.interview.risk.domain.repository.OutboxRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OutboxRelay implements AutoCloseable {
    private final OutboxRepository outbox;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxRelay(OutboxRepository outbox, ExecutorService executor) {
        this.outbox = outbox;
        this.executor = executor;
    }

    public void flushAsync() {
        if (!running.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                for (var event : outbox.pending(100)) {
                    System.out.println("published-event=" + event);
                    outbox.markPublished(event.eventId());
                }
            } finally {
                running.set(false);
            }
        });
    }

    @Override
    public void close() throws Exception {
        flushAsync();
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }
}
