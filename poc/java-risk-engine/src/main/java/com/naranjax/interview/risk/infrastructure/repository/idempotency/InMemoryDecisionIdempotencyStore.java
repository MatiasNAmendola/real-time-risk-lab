package com.naranjax.interview.risk.infrastructure.repository.idempotency;

import com.naranjax.interview.risk.domain.entity.IdempotencyKey;
import com.naranjax.interview.risk.domain.entity.RiskDecision;
import com.naranjax.interview.risk.domain.repository.DecisionIdempotencyStore;

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
