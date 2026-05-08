package io.riskplatform.servicemesh.shared;

public record RiskDecision(
    String transactionId,
    String decision,
    String reason,
    String correlationId,
    String architecture,
    String fraudRulesRecommendation,
    double mlScore
) {}
