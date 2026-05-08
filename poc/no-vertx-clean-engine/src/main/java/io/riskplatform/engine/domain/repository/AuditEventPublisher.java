package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.entity.DecisionEvent;

/**
 * Port out: publishes audit events to durable storage (S3 / MinIO).
 * Impl: S3AuditEventPublisher (Phase 2 — requires AWS SDK v2 in classpath).
 * Fallback: NoOpAuditEventPublisher (current, bare-javac without Gradle).
 */
public interface AuditEventPublisher {
    void publish(DecisionEvent event);
}
