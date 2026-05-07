package com.naranjax.interview.risk.domain.repository;

import com.naranjax.interview.risk.domain.entity.FeatureSnapshot;
import com.naranjax.interview.risk.domain.entity.TransactionRiskRequest;

/** Port out — outbound adapter. Equivalente a internal/domain/repositories/ (enterprise Go layout). */
public interface FeatureProvider {
    FeatureSnapshot getFeatures(TransactionRiskRequest request);
}
