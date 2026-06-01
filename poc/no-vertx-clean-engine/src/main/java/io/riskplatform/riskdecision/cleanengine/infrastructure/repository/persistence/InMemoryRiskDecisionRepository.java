package io.riskplatform.riskdecision.cleanengine.infrastructure.repository.persistence;

import io.riskplatform.riskdecision.cleanengine.domain.context.ExecutionContext;
import io.riskplatform.riskdecision.cleanengine.domain.entity.RiskDecision;
import io.riskplatform.riskdecision.cleanengine.domain.entity.TransactionId;
import io.riskplatform.riskdecision.cleanengine.domain.repository.RiskDecisionRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRiskDecisionRepository implements RiskDecisionRepository {
    private final ConcurrentHashMap<TransactionId, RiskDecision> decisions = new ConcurrentHashMap<>();

    @Override
    public RiskDecision create(ExecutionContext context, RiskDecision decision) {
        context.logger().info("saving risk decision", "transaction_id", decision.transactionId().value(), "decision", decision.decision());
        decisions.put(decision.transactionId(), decision);
        return decision;
    }

    @Override
    public Optional<RiskDecision> findByTransactionId(ExecutionContext context, TransactionId transactionId) {
        return Optional.ofNullable(decisions.get(transactionId));
    }
}
