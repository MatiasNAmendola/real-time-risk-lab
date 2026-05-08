package io.riskplatform.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class MotoContainer extends GenericContainer<MotoContainer> {

    private static final String IMAGE = "motoserver/moto:latest";
    public static final int MOTO_PORT = 5000;
    // Moto accepts any non-empty credentials
    public static final String ACCESS_KEY = "test";
    public static final String SECRET_KEY = "test";
    public static final String REGION = "us-east-1";

    public MotoContainer(DockerImageName image) {
        super(image);
    }

    public static MotoContainer create() {
        return new MotoContainer(DockerImageName.parse(IMAGE))
                .withExposedPorts(MOTO_PORT)
                .waitingFor(Wait.forHttp("/moto-api/reset").forPort(MOTO_PORT).withStartupTimeout(java.time.Duration.ofSeconds(30)));
    }

    public String endpointUrl() {
        return "http://" + getHost() + ":" + getMappedPort(MOTO_PORT);
    }
}
