package io.riskplatform.engine.domain.entity;

public record RiskScore(int value, String modelVersion) {
    public RiskScore {
        if (value < 0 || value > 100) throw new IllegalArgumentException("risk score must be 0..100");
        if (modelVersion == null || modelVersion.isBlank()) throw new IllegalArgumentException("model version is required");
    }
}
