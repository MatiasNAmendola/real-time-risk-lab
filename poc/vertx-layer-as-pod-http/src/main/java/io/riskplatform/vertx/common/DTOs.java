package io.riskplatform.vertx.common;

import java.time.Instant;
import java.util.List;

public final class DTOs {
    private DTOs() {}

    public record RiskRequest(String transactionId, String customerId, long amountInCents, boolean newDevice, String correlationId, String idempotencyKey) {}
    public record RiskDecision(String transactionId, String decision, String reason, long elapsedMs, DecisionTrace trace) {}
    public record DecisionTrace(String correlationId, String ruleSetVersion, String modelVersion, List<String> evaluatedRules, List<String> fallbacks, Integer mlScore) {}
    public record DecisionEvent(String eventId, String eventType, int eventVersion, Instant occurredAt, String correlationId, String transactionId, String decision, String reason, String ruleSetVersion, String modelVersion) {}
    public record IdempotencyLookup(String idempotencyKey) {}
    public record IdempotencySave(String idempotencyKey, RiskDecision decision) {}
    public record MaybeDecision(boolean found, RiskDecision decision) {}
    public record SavedDecision(RiskDecision decision) {}
    public record PendingEvents(List<DecisionEvent> events) {}
    public record Health(String pod, String status) {}
}
