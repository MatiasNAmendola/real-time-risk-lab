package io.riskplatform.rules.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Deserialized representation of a rules.yaml file.
 *
 * The hash field is computed by the loader from the file content, not taken from the YAML.
 * This ensures the hash always reflects the actual bytes on disk.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RulesConfig(
        @JsonProperty("version")            String version,
        @JsonProperty("hash")               String hash,
        @JsonProperty("deployed_at")        String deployedAt,
        @JsonProperty("deployed_by")        String deployedBy,
        @JsonProperty("environment")        String environment,
        @JsonProperty("aggregation_policy") String aggregationPolicy,
        @JsonProperty("timeout_ms")         int timeoutMs,
        @JsonProperty("fallback_decision")  String fallbackDecision,
        @JsonProperty("rules")              List<RuleDefinition> rules,
        @JsonProperty("audit")              List<AuditEntry> audit
) {

    /**
     * Raw rule definition as parsed from YAML.
     * The engine compiles these into FraudRule instances at load time.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleDefinition(
            @JsonProperty("name")     String name,
            @JsonProperty("version")  String version,
            @JsonProperty("type")     String type,
            @JsonProperty("enabled")  boolean enabled,
            @JsonProperty("weight")   double weight,
            @JsonProperty("action")   String action,
            @JsonProperty("parameters") java.util.Map<String, Object> parameters,
            @JsonProperty("metadata") RuleMetadata metadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleMetadata(
            @JsonProperty("owner")              String owner,
            @JsonProperty("created_at")         String createdAt,
            @JsonProperty("last_modified_by")   String lastModifiedBy,
            @JsonProperty("last_modified_at")   String lastModifiedAt,
            @JsonProperty("deployment_env")     List<String> deploymentEnv
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuditEntry(
            @JsonProperty("timestamp")      String timestamp,
            @JsonProperty("actor")          String actor,
            @JsonProperty("action")         String action,
            @JsonProperty("rule")           String rule,
            @JsonProperty("diff")           String diff,
            @JsonProperty("reason")         String reason,
            @JsonProperty("correlation_id") String correlationId
    ) {}

    /** Returns a copy with a new hash (used by loader after computing the actual hash). */
    public RulesConfig withHash(String computedHash) {
        return new RulesConfig(version, computedHash, deployedAt, deployedBy, environment,
                aggregationPolicy, timeoutMs, fallbackDecision, rules, audit);
    }

    /** Number of enabled rules. */
    public long enabledCount() {
        if (rules == null) return 0;
        return rules.stream().filter(RuleDefinition::enabled).count();
    }
}
