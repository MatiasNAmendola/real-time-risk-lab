package com.naranjax.interview.risk.domain.usecase;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.domain.entity.RiskDecision;
import com.naranjax.interview.risk.domain.entity.TransactionRiskRequest;

import java.time.Duration;

/** Port in — inbound adapter. Equivalente a internal/domain/usecases/ (enterprise Go layout). */
public interface EvaluateRiskUseCase {
    RiskDecision evaluate(ExecutionContext context, TransactionRiskRequest request, Duration maxLatency);
}
