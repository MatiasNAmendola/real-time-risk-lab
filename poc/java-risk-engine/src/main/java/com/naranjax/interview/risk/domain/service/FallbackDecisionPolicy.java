package com.naranjax.interview.risk.domain.service;

import com.naranjax.interview.risk.domain.entity.*;

public final class FallbackDecisionPolicy {
    public FallbackDecision decide(TransactionRiskRequest request, FeatureSnapshot features) {
        if (request.amount().cents() > 20_000 || request.newDevice() || features.chargebackCount90d() > 0) {
            return new FallbackDecision(Decision.REVIEW, "risk-sensitive-fallback");
        }
        return new FallbackDecision(Decision.APPROVE, "safe-approve-fallback");
    }

    public record FallbackDecision(Decision decision, String reason) {}
}
