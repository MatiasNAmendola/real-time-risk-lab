package io.riskplatform.engine.application.mapper;

import io.riskplatform.engine.application.dto.*;
import io.riskplatform.engine.domain.entity.*;

public final class RiskDecisionMapper {
    private RiskDecisionMapper() {}

    public static TransactionRiskRequest toDomain(EvaluateRiskRequestDTO dto) {
        return new TransactionRiskRequest(
                new TransactionId(dto.transactionId()),
                new CustomerId(dto.customerId()),
                new Money(dto.amountInCents(), dto.currency()),
                dto.newDevice(),
                new CorrelationId(dto.correlationId()),
                new IdempotencyKey(dto.idempotencyKey())
        );
    }

    public static RiskDecisionResponseDTO toDTO(RiskDecision decision) {
        return new RiskDecisionResponseDTO(
                decision.transactionId().value(),
                decision.decision().name(),
                decision.reason(),
                decision.elapsed().toMillis(),
                decision.trace().correlationId().value(),
                decision.trace().ruleSetVersion(),
                decision.trace().modelVersion(),
                decision.trace().evaluatedRules(),
                decision.trace().fallbacks()
        );
    }
}
