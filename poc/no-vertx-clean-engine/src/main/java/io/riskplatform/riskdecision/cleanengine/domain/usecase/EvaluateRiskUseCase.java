package io.riskplatform.riskdecision.cleanengine.domain.usecase;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;
import io.riskplatform.riskdecision.cleanengine.domain.entity.RiskDecision;
import io.riskplatform.riskdecision.cleanengine.domain.entity.TransactionRiskRequest;

import java.time.Duration;

/** Port in — inbound adapter. Equivalente a internal/domain/usecases/ (enterprise Go layout). */
public interface EvaluateRiskUseCase {
    RiskDecision evaluate(ExecutionContext context, TransactionRiskRequest request, Duration maxLatency);
}
