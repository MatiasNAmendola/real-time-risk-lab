package com.naranjax.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class ValkeyContainer extends GenericContainer<ValkeyContainer> {

    private static final String IMAGE = "valkey/valkey:8-alpine";
    public static final int VALKEY_PORT = 6379;

    public ValkeyContainer(DockerImageName image) {
        super(image);
    }

    public static ValkeyContainer create() {
        return new ValkeyContainer(DockerImageName.parse(IMAGE))
                .withExposedPorts(VALKEY_PORT)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
    }

    public String getRedisUrl() {
        return "redis://" + getHost() + ":" + getMappedPort(VALKEY_PORT);
    }
}
