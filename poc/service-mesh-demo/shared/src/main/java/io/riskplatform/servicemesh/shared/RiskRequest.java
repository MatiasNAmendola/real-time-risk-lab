package io.riskplatform.servicemesh.shared;

public record RiskRequest(
    String transactionId,
    String customerId,
    long amountCents,
    String correlationId,
    boolean newDevice
) {}
