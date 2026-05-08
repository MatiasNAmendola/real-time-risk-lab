package io.riskplatform.engine.infrastructure.repository.audit;

import io.riskplatform.engine.domain.entity.DecisionEvent;
import io.riskplatform.engine.domain.repository.AuditEventPublisher;

/**
 * Fallback AuditEventPublisher used when AWS_ENDPOINT_URL_S3 is not set.
 * Phase 2 (Gradle): replace with S3AuditEventPublisher backed by MinIO/S3.
 *
 * TODO(phase-2): wire S3AuditEventPublisher once AWS SDK v2 is in classpath via Gradle.
 */
public final class NoOpAuditEventPublisher implements AuditEventPublisher {
    @Override
    public void publish(DecisionEvent event) {
        // Intentionally empty — no S3 endpoint configured.
        // Set AWS_ENDPOINT_URL_S3 and rebuild with Gradle to enable real audit publishing.
    }
}
