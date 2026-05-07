package com.naranjax.atdd.support;

import com.naranjax.interview.risk.domain.repository.ClockPort;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic clock for ATDD tests.
 * Defaults to a fixed epoch so tests are reproducible.
 * Call {@link #advanceBy(java.time.Duration)} to simulate time passing.
 */
public final class ClockStub implements ClockPort {

    private final AtomicReference<Instant> current =
            new AtomicReference<>(Instant.parse("2026-05-07T12:00:00Z"));

    @Override
    public Instant now() {
        return current.get();
    }

    public void advanceBy(java.time.Duration duration) {
        current.updateAndGet(t -> t.plus(duration));
    }

    public void set(Instant instant) {
        current.set(instant);
    }
}
