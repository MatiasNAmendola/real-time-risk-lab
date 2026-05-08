package io.riskplatform.engine.domain.entity;

public record TransactionRiskRequest(
        TransactionId transactionId,
        CustomerId customerId,
        Money amount,
        boolean newDevice,
        CorrelationId correlationId,
        IdempotencyKey idempotencyKey
) {
}
