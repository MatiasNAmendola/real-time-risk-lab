package io.riskplatform.riskdecision.cleanengine.domain.repository;

import io.riskplatform.riskdecision.cleanengine.domain.entity.IdempotencyKey;
import io.riskplatform.riskdecision.cleanengine.domain.entity.RiskDecision;

import java.util.Optional;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface DecisionIdempotencyStore {
    Optional<RiskDecision> find(IdempotencyKey key);
    RiskDecision saveIfAbsent(IdempotencyKey key, RiskDecision decision);
}
