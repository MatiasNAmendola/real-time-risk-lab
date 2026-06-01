package io.riskplatform.riskdecision.cleanengine.infrastructure.time;

import io.riskplatform.riskdecision.cleanengine.domain.repository.ClockPort;

import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {
    @Override public Instant now() { return Instant.now(); }
}
