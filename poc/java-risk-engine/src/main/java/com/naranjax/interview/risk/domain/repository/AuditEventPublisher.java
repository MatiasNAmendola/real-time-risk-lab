package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.entity.DecisionEvent;

/**
 * Port out: publishes audit events to durable storage (S3 / MinIO).
 * Impl: S3AuditEventPublisher (Phase 2 — requires AWS SDK v2 in classpath).
 * Fallback: NoOpAuditEventPublisher (current, bare-javac without Maven).
 */
public interface AuditEventPublisher {
    void publish(DecisionEvent event);
}
