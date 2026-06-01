package io.riskplatform.riskdecision.cleanengine.domain.repository;

import java.time.Instant;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface ClockPort {
    Instant now();
}
