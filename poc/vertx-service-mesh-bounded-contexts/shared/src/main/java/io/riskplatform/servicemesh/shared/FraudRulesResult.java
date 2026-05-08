package io.riskplatform.servicemesh.shared;

import java.util.List;

public record FraudRulesResult(List<String> firedRules, String recommendation, int riskPoints) {}
