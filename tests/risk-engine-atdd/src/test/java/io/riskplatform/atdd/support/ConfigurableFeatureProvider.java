package io.riskplatform.atdd.support;

import io.riskplatform.engine.domain.entity.FeatureSnapshot;
import io.riskplatform.engine.domain.entity.TransactionRiskRequest;
import io.riskplatform.engine.domain.repository.FeatureProvider;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only FeatureProvider that lets scenarios seed exact feature values
 * per customer id, enabling deterministic rule evaluation without modifying the PoC.
 * <p>
 * Falls back to hash-based derivation (same as the production InMemoryFeatureProvider)
 * when no explicit seed is registered.
 */
public final class ConfigurableFeatureProvider implements FeatureProvider {

    private final ConcurrentHashMap<String, FeatureSnapshot> seeded = new ConcurrentHashMap<>();

    /** Registers (or overrides) features for a customer. Steps call this; last write wins. */
    public void seed(String customerId, int customerAgeDays, int chargebackCount90d) {
        seeded.put(customerId, new FeatureSnapshot(customerAgeDays, chargebackCount90d));
    }

    @Override
    public FeatureSnapshot getFeatures(TransactionRiskRequest request) {
        // Always use an explicit seed if present, else fall back to hash-derivation.
        return seeded.computeIfAbsent(request.customerId().value(), id -> {
            int ageDays = Math.abs(id.hashCode() % 365);
            int chargebacks = Math.abs(request.transactionId().value().hashCode() % 3);
            return new FeatureSnapshot(ageDays, chargebacks);
        });
    }
}
