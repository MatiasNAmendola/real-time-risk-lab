package io.riskplatform.rules.client.sync;

import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;

import java.util.Arrays;
import java.util.List;

/**
 * Synchronous REST channel. Backed by java.net.http.HttpClient.
 */
public final class SyncClient {

    private final JsonHttpClient http;
    private final String baseUrl;

    public SyncClient(ClientConfig config, JsonHttpClient http) {
        this.http    = http;
        this.baseUrl = config.environment().restBaseUrl();
    }

    /** Evaluate a single risk request and return the decision synchronously. */
    public RiskDecision evaluate(RiskRequest req) {
        return http.postJson(baseUrl + "/risk", req, RiskDecision.class);
    }

    /** Evaluate multiple requests in one batch call. */
    public List<RiskDecision> evaluateBatch(List<RiskRequest> requests) {
        RiskDecision[] decisions = http.postJson(
                baseUrl + "/risk/batch", requests, RiskDecision[].class);
        return Arrays.asList(decisions);
    }

    /** Check liveness of the risk engine. */
    public HealthStatus health() {
        return http.getJson(baseUrl + "/healthz", HealthStatus.class);
    }
}
