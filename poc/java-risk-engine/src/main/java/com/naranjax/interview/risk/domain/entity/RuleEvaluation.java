package com.naranjax.interview.risk.domain.entity;

public record RuleEvaluation(String ruleName, boolean matched, Decision decision, String reason) {
    public static RuleEvaluation noMatch(String ruleName) {
        return new RuleEvaluation(ruleName, false, Decision.APPROVE, "no-match");
    }
}
