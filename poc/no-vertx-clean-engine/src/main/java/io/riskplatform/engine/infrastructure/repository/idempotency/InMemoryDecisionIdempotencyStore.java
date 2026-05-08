package io.riskplatform.engine.infrastructure.repository.idempotency;

import io.riskplatform.engine.domain.entity.IdempotencyKey;
import io.riskplatform.engine.domain.entity.RiskDecision;
import io.riskplatform.engine.domain.repository.DecisionIdempotencyStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDecisionIdempotencyStore implements DecisionIdempotencyStore {
    private final ConcurrentHashMap<IdempotencyKey, RiskDecision> decisions = new ConcurrentHashMap<>();

    @Override public Optional<RiskDecision> find(IdempotencyKey key) { return Optional.ofNullable(decisions.get(key)); }

    @Override
    public RiskDecision saveIfAbsent(IdempotencyKey key, RiskDecision decision) {
        return decisions.putIfAbsent(key, decision) == null ? decision : decisions.get(key);
    }
}
