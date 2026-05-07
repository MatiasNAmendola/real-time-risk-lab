package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.entity.FeatureSnapshot;
import com.naranjax.interview.risk.domain.entity.RiskScore;
import com.naranjax.interview.risk.domain.entity.TransactionRiskRequest;

import java.time.Duration;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface RiskModelScorer {
    String modelVersion();
    RiskScore score(TransactionRiskRequest request, FeatureSnapshot features, Duration timeout) throws Exception;
}
