package io.riskplatform.monolith.unit;

import io.riskplatform.distributed.shared.RiskDecision;
import io.riskplatform.distributed.shared.RiskRequest;
import io.riskplatform.monolith.repository.KafkaDecisionPublisher;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KafkaDecisionPublisher in disabled mode.
 * When KAFKA_BOOTSTRAP_SERVERS is not set, publish must be a no-op.
 */
class KafkaDecisionPublisherTest {

    private final KafkaDecisionPublisher publisher = new KafkaDecisionPublisher();

    @Test
    void publish_isNoOp_whenKafkaNotConfigured() {
        RiskRequest req = new RiskRequest("tx-001", "c-1", 10000L, "corr-1", null, false);
        RiskDecision decision = new RiskDecision("APPROVE", "within limits", "corr-1");
        // Should not throw even when Kafka is disabled
        publisher.publish(req, decision, "corr-1", "risk-decisions");
    }

    @Test
    void publish_doesNotThrow_withDeclineDecision() {
        RiskRequest req = new RiskRequest("tx-002", "c-4", 200000L, "corr-2", null, false);
        RiskDecision decision = new RiskDecision("DECLINE", "high amount", "corr-2");
        publisher.publish(req, decision, "corr-2", "risk-decisions");
    }
}
