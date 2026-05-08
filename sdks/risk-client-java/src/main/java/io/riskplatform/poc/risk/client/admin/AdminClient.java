package io.riskplatform.rules.client.admin;

import io.riskplatform.rules.client.http.JsonHttpClient;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Admin channel for rule management and audit operations.
 */
public final class AdminClient {

    private final JsonHttpClient http;
    private final String baseUrl;

    public AdminClient(ClientConfig config, JsonHttpClient http) {
        this.http    = http;
        this.baseUrl = config.environment().restBaseUrl();
    }

    /** List all currently active rules. */
    public List<RuleInfo> listRules() {
        RuleInfo[] rules = http.getJson(baseUrl + "/admin/rules", RuleInfo[].class);
        return Arrays.asList(rules);
    }

    /** Trigger a hot-reload of rules from the rule store. */
    public void reloadRules() {
        http.postJson(baseUrl + "/admin/rules/reload", Map.of(), Void.class);
    }

    /** Evaluate a request against the current rule set without persisting the decision. */
    public RiskDecision testRule(RiskRequest req) {
        return http.postJson(baseUrl + "/admin/rules/test", req, RiskDecision.class);
    }

    /** Fetch the immutable audit trail for rule changes. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rulesAuditTrail() {
        Map<String, Object>[] entries = http.getJson(
                baseUrl + "/admin/rules/audit", Map[].class);
        return Arrays.asList(entries);
    }
}
