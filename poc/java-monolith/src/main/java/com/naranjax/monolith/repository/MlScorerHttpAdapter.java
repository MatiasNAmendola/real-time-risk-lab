package com.naranjax.monolith.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * HTTP adapter for the optional ML scorer service.
 *
 * <p>In the PoC this calls a local stub or a fake endpoint.
 * In production this would call a model-serving endpoint (TorchServe, Seldon, etc.).
 *
 * <p>The circuit breaker in EvaluateRiskUseCase wraps calls to this adapter.
 * If the scorer is unavailable, the pre-materialised DB score is used instead.
 *
 * <p>Env var: ML_SCORER_URL (if not set, always returns 0.5)
 */
public class MlScorerHttpAdapter {

    private static final Logger log = LoggerFactory.getLogger(MlScorerHttpAdapter.class);
    private static final double DEFAULT_SCORE = 0.5;

    private final String scorerUrl;
    private final boolean enabled;

    public MlScorerHttpAdapter() {
        String url = System.getenv().getOrDefault("ML_SCORER_URL", "");
        this.scorerUrl = url;
        this.enabled   = !url.isBlank();
        if (!enabled) {
            log.info("[monolith] MlScorerHttpAdapter disabled (ML_SCORER_URL not set) — using pre-materialised scores");
        }
    }

    /**
     * Calls the ML scorer endpoint and returns a score in [0.0, 1.0].
     * Throws on HTTP error or timeout — caller should wrap in circuit breaker.
     * BLOCKS — call from a worker thread.
     */
    public double score(String customerId, String transactionId, long amountCents) throws Exception {
        if (!enabled) return DEFAULT_SCORE;

        String requestBody = "{"
            + "\"customerId\":\"" + customerId + "\","
            + "\"transactionId\":\"" + transactionId + "\","
            + "\"amountCents\":" + amountCents
            + "}";

        URI uri = URI.create(scorerUrl + "/score");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes());
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("ML scorer HTTP " + status);
        }

        byte[] bytes = conn.getInputStream().readAllBytes();
        String response = new String(bytes);
        // Expect {"score": 0.73}
        int idx = response.indexOf("\"score\":");
        if (idx < 0) throw new RuntimeException("ML scorer response missing 'score' field");
        String scoreStr = response.substring(idx + 8).replaceAll("[^0-9.]", "").trim();
        double parsed = Double.parseDouble(scoreStr);
        log.debug("[monolith] ML scorer: customerId={} score={}", customerId, parsed);
        return parsed;
    }
}
