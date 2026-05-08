package io.riskplatform.engine.domain.service;

import io.riskplatform.engine.domain.entity.Decision;
import io.riskplatform.engine.domain.entity.RiskScore;

public final class ScoreDecisionPolicy {
    public ScoreDecision classify(RiskScore score) {
        if (score.value() >= 85) return new ScoreDecision(Decision.DECLINE, "ml-score-high");
        if (score.value() >= 60) return new ScoreDecision(Decision.REVIEW, "ml-score-medium");
        return new ScoreDecision(Decision.APPROVE, "low-risk");
    }

    public record ScoreDecision(Decision decision, String reason) {}
}
