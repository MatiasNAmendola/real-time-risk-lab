package io.riskplatform.riskdecision.cleanengine.domain.entity;

public record FeatureSnapshot(int customerAgeDays, int chargebackCount90d) {
}
