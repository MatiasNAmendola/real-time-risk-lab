package com.naranjax.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class MinioContainer extends GenericContainer<MinioContainer> {

    private static final String IMAGE = "minio/minio:RELEASE.2024-11-07T00-52-20Z";
    public static final int API_PORT = 9000;
    public static final int CONSOLE_PORT = 9001;
    public static final String ROOT_USER = "minioadmin";
    public static final String ROOT_PASSWORD = "minioadmin";

    public MinioContainer(DockerImageName image) {
        super(image);
    }

    public static MinioContainer create() {
        return new MinioContainer(DockerImageName.parse(IMAGE))
                .withCommand("server /data --console-address :9001")
                .withEnv("MINIO_ROOT_USER", ROOT_USER)
                .withEnv("MINIO_ROOT_PASSWORD", ROOT_PASSWORD)
                .withExposedPorts(API_PORT, CONSOLE_PORT)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(API_PORT).withStartupTimeout(java.time.Duration.ofSeconds(30)));
    }

    public String s3Endpoint() {
        return "http://" + getHost() + ":" + getMappedPort(API_PORT);
    }
}
