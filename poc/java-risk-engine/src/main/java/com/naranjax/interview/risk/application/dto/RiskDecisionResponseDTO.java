package com.naranjax.interview.risk.application.dto;

import java.util.List;

public record RiskDecisionResponseDTO(
        String transactionId,
        String decision,
        String reason,
        long elapsedMs,
        String correlationId,
        String ruleSetVersion,
        String modelVersion,
        List<String> evaluatedRules,
        List<String> fallbacks
) {
}
