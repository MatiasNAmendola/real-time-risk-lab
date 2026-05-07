package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.entity.IdempotencyKey;
import com.naranjax.interview.risk.domain.entity.RiskDecision;

import java.util.Optional;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionIdempotencyStore {
    Optional<RiskDecision> find(IdempotencyKey key);
    RiskDecision saveIfAbsent(IdempotencyKey key, RiskDecision decision);
}
