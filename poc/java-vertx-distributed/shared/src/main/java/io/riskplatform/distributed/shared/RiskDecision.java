package io.riskplatform.distributed.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decision returned from usecase to controller and ultimately to the HTTP client.
 */
public record RiskDecision(
        @JsonProperty("decision")      String decision,
        @JsonProperty("reason")        String reason,
        @JsonProperty("correlationId") String correlationId
) {
    @JsonCreator
    public RiskDecision {}

    public static RiskDecision approve(String correlationId) {
        return new RiskDecision("APPROVE", "transaction within limits", correlationId);
    }

    public static RiskDecision review(String correlationId) {
        return new RiskDecision("REVIEW", "high amount", correlationId);
    }

    public static RiskDecision decline(String correlationId) {
        return new RiskDecision("DECLINE", "high amount + high score", correlationId);
    }

    // Backwards-compat factory without correlationId
    public static RiskDecision approve()  { return approve(null); }
    public static RiskDecision review()   { return review(null);  }
    public static RiskDecision decline()  { return decline(null); }
}
