package io.riskplatform.engine.infrastructure.repository.feature;

import io.riskplatform.engine.domain.entity.FeatureSnapshot;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;
import io.riskplatform.engine.domain.repository.FeatureProvider;

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
