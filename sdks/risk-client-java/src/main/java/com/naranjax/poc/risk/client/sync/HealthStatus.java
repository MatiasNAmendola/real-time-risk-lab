package com.naranjax.poc.risk.client.sync;

/**
 * Response from the health endpoint.
 */
public record HealthStatus(String status, String version) {
    public boolean isUp() { return "UP".equalsIgnoreCase(status); }
}
