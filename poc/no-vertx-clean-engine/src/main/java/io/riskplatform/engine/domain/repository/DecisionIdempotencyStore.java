package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.entity.IdempotencyKey;
import io.riskplatform.engine.domain.entity.RiskDecision;

import java.util.Optional;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionIdempotencyStore {
    Optional<RiskDecision> find(IdempotencyKey key);
    RiskDecision saveIfAbsent(IdempotencyKey key, RiskDecision decision);
}
