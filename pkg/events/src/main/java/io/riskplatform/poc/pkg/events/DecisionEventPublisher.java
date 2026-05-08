package io.riskplatform.poc.pkg.events;

/**
 * Outbound port for publishing decision events.
 * Copied from poc/no-vertx-clean-engine, re-packaged.
 * Original: io.riskplatform.engine.domain.repository.DecisionEventPublisher
 */
public interface DecisionEventPublisher {
    void publish(DecisionEvent event);
}
