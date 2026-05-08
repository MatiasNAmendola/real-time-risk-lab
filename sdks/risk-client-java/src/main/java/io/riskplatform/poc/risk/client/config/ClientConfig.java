package io.riskplatform.rules.client.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for RiskClient. Build via {@link #builder()}.
 */
public final class ClientConfig {

    private final Environment environment;
    private final String apiKey;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;
    private final String otlpEndpoint;

    private ClientConfig(Builder b) {
        this.environment  = Objects.requireNonNull(b.environment,  "environment is required");
        this.apiKey       = Objects.requireNonNull(b.apiKey,       "apiKey is required");
        this.timeout      = b.timeout != null ? b.timeout : Duration.ofMillis(280);
        this.retryPolicy  = b.retryPolicy != null ? b.retryPolicy : RetryPolicy.exponentialBackoff();
        this.otlpEndpoint = b.otlpEndpoint;
    }

    public static Builder builder() { return new Builder(); }

    public Environment environment()   { return environment; }
    public String apiKey()             { return apiKey; }
    public Duration timeout()          { return timeout; }
    public RetryPolicy retryPolicy()   { return retryPolicy; }
    public String otlpEndpoint()       { return otlpEndpoint; }

    public static final class Builder {
        private Environment environment;
        private String apiKey;
        private Duration timeout;
        private RetryPolicy retryPolicy;
        private String otlpEndpoint;

        public Builder environment(Environment e)   { this.environment = e;  return this; }
        public Builder apiKey(String k)             { this.apiKey = k;       return this; }
        public Builder timeout(Duration d)          { this.timeout = d;      return this; }
        public Builder retry(RetryPolicy p)         { this.retryPolicy = p;  return this; }
        public Builder otlp(String endpoint)        { this.otlpEndpoint = endpoint; return this; }

        public ClientConfig build() { return new ClientConfig(this); }
    }
}
