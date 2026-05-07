package com.naranjax.interview.risk.infrastructure.repository.ml;

import com.naranjax.interview.risk.domain.entity.*;
import com.naranjax.interview.risk.domain.repository.RiskModelScorer;

import java.time.Duration;
import java.util.random.RandomGenerator;

public final class FakeRiskModelScorer implements RiskModelScorer {
    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");

    @Override public String modelVersion() { return "fraud-model-2026-05-07"; }

    @Override
    public RiskScore score(TransactionRiskRequest request, FeatureSnapshot features, Duration timeout) throws Exception {
        long simulatedLatencyMs = 20 + random.nextLong(140);
        if (timeout.toMillis() < simulatedLatencyMs) {
            Thread.sleep(Math.max(1, timeout.toMillis()));
            throw new ModelTimeoutException("ml-timeout budgetMs=" + timeout.toMillis());
        }
        Thread.sleep(simulatedLatencyMs);
        if (random.nextDouble() < 0.15) throw new RuntimeException("ml-temporary-error");

        int amountSignal = (int) Math.min(70, request.amount().cents() / 1_000);
        int deviceSignal = request.newDevice() ? 20 : 0;
        int chargebackSignal = features.chargebackCount90d() * 15;
        return new RiskScore(Math.min(100, amountSignal + deviceSignal + chargebackSignal), modelVersion());
    }
}
