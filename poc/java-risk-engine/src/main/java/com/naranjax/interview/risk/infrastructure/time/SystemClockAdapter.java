package com.naranjax.interview.risk.infrastructure.time;

import com.naranjax.interview.risk.domain.repository.ClockPort;

import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {
    @Override public Instant now() { return Instant.now(); }
}
