package com.naranjax.interview.risk.application.common;

/**
 * Tombstone — this class has been moved to
 * {@link com.naranjax.interview.risk.domain.context.ExecutionContext}
 * as part of the Phase 2 ArchUnit fix (domain must not depend on application).
 *
 * All production imports were updated by the automated migration on 2026-05-07.
 * This file can be removed in Phase 3 once all consumers confirm no references remain.
 */
@Deprecated(since = "phase-2", forRemoval = true)
public final class ExecutionContext {
    private ExecutionContext() {
        throw new UnsupportedOperationException("Use domain.context.ExecutionContext");
    }
}
