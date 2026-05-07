package com.naranjax.interview.risk.infrastructure.repository.feature;

import com.naranjax.interview.risk.domain.entity.FeatureSnapshot;
import com.naranjax.interview.risk.domain.entity.TransactionRiskRequest;
import com.naranjax.interview.risk.domain.repository.FeatureProvider;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFeatureProvider implements FeatureProvider {
    private final ConcurrentHashMap<String, FeatureSnapshot> cache = new ConcurrentHashMap<>();

    @Override
    public FeatureSnapshot getFeatures(TransactionRiskRequest request) {
        return cache.computeIfAbsent(request.customerId().value(), ignored -> {
            int ageDays = Math.abs(request.customerId().value().hashCode() % 365);
            int chargebacks = Math.abs(request.transactionId().value().hashCode() % 3);
            return new FeatureSnapshot(ageDays, chargebacks);
        });
    }
}
