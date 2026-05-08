package io.riskplatform.rules.client.sync;

/**
 * Response from the health endpoint.
 */
public record HealthStatus(String status, String version) {
    public boolean isUp() { return "UP".equalsIgnoreCase(status); }
}
