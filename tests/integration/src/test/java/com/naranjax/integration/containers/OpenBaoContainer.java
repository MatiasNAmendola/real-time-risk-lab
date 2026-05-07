package com.naranjax.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class OpenBaoContainer extends GenericContainer<OpenBaoContainer> {

    private static final String IMAGE = "openbao/openbao:2.1.0";
    public static final int BAO_PORT = 8200;
    public static final String ROOT_TOKEN = "root-test-token";

    public OpenBaoContainer(DockerImageName image) {
        super(image);
    }

    public static OpenBaoContainer create() {
        return new OpenBaoContainer(DockerImageName.parse(IMAGE))
                // Dev mode: in-memory storage, auto-initialized, root token set via env
                .withEnv("BAO_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
                .withEnv("BAO_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
                .withCommand("server", "-dev")
                .withExposedPorts(BAO_PORT)
                .waitingFor(Wait.forHttp("/v1/sys/health").forPort(BAO_PORT).withStartupTimeout(java.time.Duration.ofSeconds(30)));
    }

    public String baseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(BAO_PORT);
    }
}
