package io.riskplatform.engine.infrastructure.time;

import io.riskplatform.engine.domain.repository.ClockPort;

import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {
    @Override public Instant now() { return Instant.now(); }
}
