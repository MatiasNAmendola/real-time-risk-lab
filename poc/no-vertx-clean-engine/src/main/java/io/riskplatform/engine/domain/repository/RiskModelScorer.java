package io.riskplatform.engine.domain.repository;

import io.riskplatform.engine.domain.entity.FeatureSnapshot;
import io.riskplatform.engine.domain.entity.RiskScore;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;

import java.time.Duration;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface RiskModelScorer {
    String modelVersion();
    RiskScore score(TransactionRiskRequest request, FeatureSnapshot features, Duration timeout) throws Exception;
}
