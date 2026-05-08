package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.entity.FeatureSnapshot;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface FeatureProvider {
    FeatureSnapshot getFeatures(TransactionRiskRequest request);
}
