package io.riskplatform.servicemesh.shared;

public record MlScoreResult(double score, String modelVersion, boolean fallback) {}
