package io.riskplatform.engine.domain.usecase;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.domain.entity.RiskDecision;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;

import java.time.Duration;

/** Port in — inbound adapter. Equivalente a internal/domain/usecases/ (enterprise Go layout). */
public interface EvaluateRiskUseCase {
    RiskDecision evaluate(ExecutionContext context, TransactionRiskRequest request, Duration maxLatency);
}
