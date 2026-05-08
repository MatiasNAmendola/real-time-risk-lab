package io.riskplatform.integration.containers;

/**
 * Factory for the official Testcontainers Redpanda module.
 * The official {@code org.testcontainers.redpanda.RedpandaContainer} is used directly;
 * this class provides a single create() entry point for consistency.
 */
public final class RedpandaContainer {

    private RedpandaContainer() {}

    public static org.testcontainers.redpanda.RedpandaContainer create() {
        return new org.testcontainers.redpanda.RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.7");
    }
}
