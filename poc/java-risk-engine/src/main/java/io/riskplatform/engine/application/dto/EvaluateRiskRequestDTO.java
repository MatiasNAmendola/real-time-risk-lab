package io.riskplatform.engine.application.dto;

public record EvaluateRiskRequestDTO(
        String transactionId,
        String customerId,
        long amountInCents,
        String currency,
        boolean newDevice,
        String correlationId,
        String idempotencyKey
) {
}
