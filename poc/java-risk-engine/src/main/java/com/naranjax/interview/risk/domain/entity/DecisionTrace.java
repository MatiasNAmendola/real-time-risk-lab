package com.naranjax.interview.risk.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DecisionTrace {
    private final CorrelationId correlationId;
    private final String ruleSetVersion;
    private final Instant startedAt;
    private final List<String> evaluatedRules = new ArrayList<>();
    private final List<String> fallbacks = new ArrayList<>();
    private RiskScore riskScore;

    public DecisionTrace(CorrelationId correlationId, String ruleSetVersion, Instant startedAt) {
        this.correlationId = correlationId;
        this.ruleSetVersion = ruleSetVersion;
        this.startedAt = startedAt;
    }

    public void recordRule(RuleEvaluation evaluation) {
        evaluatedRules.add(evaluation.ruleName() + "=" + evaluation.matched());
    }

    public void recordScore(RiskScore score) {
        this.riskScore = score;
    }

    public void recordFallback(String reason) {
        fallbacks.add(reason);
    }

    public CorrelationId correlationId() { return correlationId; }
    public String ruleSetVersion() { return ruleSetVersion; }
    public String modelVersion() { return riskScore == null ? "not-called" : riskScore.modelVersion(); }
    public List<String> evaluatedRules() { return List.copyOf(evaluatedRules); }
    public List<String> fallbacks() { return List.copyOf(fallbacks); }

    @Override
    public String toString() {
        return "DecisionTrace{" +
                "correlationId=" + correlationId.value() +
                ", ruleSetVersion='" + ruleSetVersion + '\'' +
                ", startedAt=" + startedAt +
                ", evaluatedRules=" + evaluatedRules +
                ", fallbacks=" + fallbacks +
                ", riskScore=" + riskScore +
                '}';
    }
}
