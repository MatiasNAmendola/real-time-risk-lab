package io.riskplatform.servicemesh.mlscorer.domain.service;

import io.riskplatform.servicemesh.shared.MlScoreResult;
import io.riskplatform.servicemesh.shared.RiskRequest;

/** Deterministic fake scorer: separate bounded context with own deploy/runtime knobs. */
public final class MlScoringService {
    public MlScoreResult score(RiskRequest request) {
        double amountSignal = Math.min(0.85, request.amountCents() / 1_000_000.0);
        double deviceSignal = request.newDevice() ? 0.18 : 0.0;
        double score = Math.min(0.99, amountSignal + deviceSignal);
        return new MlScoreResult(score, "demo-logreg-2026-05", false);
    }
}
