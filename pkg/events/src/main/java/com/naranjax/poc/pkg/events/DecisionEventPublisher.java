package com.naranjax.poc.pkg.events;

/**
 * Outbound port for publishing decision events.
 * Copied from poc/java-risk-engine, re-packaged.
 * Original: com.naranjax.interview.risk.domain.repository.DecisionEventPublisher
 */
public interface DecisionEventPublisher {
    void publish(DecisionEvent event);
}
