package io.riskplatform.riskdecision.cleanengine.domain.repository;

import io.riskplatform.riskdecision.cleanengine.domain.entity.FeatureSnapshot;
import io.riskplatform.riskdecision.cleanengine.domain.entity.TransactionRiskRequest;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface FeatureProvider {
    FeatureSnapshot getFeatures(TransactionRiskRequest request);
}
