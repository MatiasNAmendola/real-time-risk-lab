package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.context.ExecutionContext;
import com.naranjax.interview.risk.domain.entity.RiskDecision;
import com.naranjax.interview.risk.domain.entity.TransactionId;

import java.util.Optional;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface RiskDecisionRepository {
    RiskDecision create(ExecutionContext context, RiskDecision decision);
    Optional<RiskDecision> findByTransactionId(ExecutionContext context, TransactionId transactionId);
}
