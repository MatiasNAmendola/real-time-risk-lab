package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.audit;

import io.riskplatform.riskdecision.cleanengine.domain.entity.DecisionEvent;
import io.riskplatform.riskdecision.cleanengine.domain.repository.AuditEventPublisher;

/**
 * Fallback AuditEventPublisher used when no S3 endpoint is configured.
 * Phase 2 (Gradle): replace with S3AuditEventPublisher backed by the Floci AWS emulator
 * (ADR-0042).
 *
 * TODO(phase-2): wire S3AuditEventPublisher once AWS SDK v2 is in classpath via Gradle.
 */
public final class NoOpAuditEventPublisher implements AuditEventPublisher {
    @Override
    public void publish(DecisionEvent event) {
        // Intentionally empty — no S3 endpoint configured.
        // Set FLOCI_ENDPOINT and rebuild with Gradle to enable real audit publishing.
    }
}
