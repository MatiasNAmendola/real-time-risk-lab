package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.context.ExecutionContext;
import io.riskplatform.engine.domain.entity.RiskDecision;
import io.riskplatform.engine.domain.entity.TransactionId;

import java.util.Optional;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface RiskDecisionRepository {
    RiskDecision create(ExecutionContext context, RiskDecision decision);
    Optional<RiskDecision> findByTransactionId(ExecutionContext context, TransactionId transactionId);
}
