package io.riskplatform.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers wrapper for the Floci unified AWS emulator (ADR-0042).
 *
 * <p>Single container at {@code :4566} exposes the AWS wire protocol for S3, SQS, SNS,
 * Secrets Manager, KMS, STS, IAM, DynamoDB and more. Replaces the previous
 * MinIO + ElasticMQ + Moto + OpenBao stack used by the integration test suite.
 *
 * <p>Image: {@code floci/floci:latest} (GraalVM native binary, MIT, ~40 MB).
 * Health endpoint: {@code GET /_floci/health}.
 *
 * <p>Credentials are the conventional Floci dummy values ({@code test/test}).
 */
public final class FlociContainer extends GenericContainer<FlociContainer> {

    private static final String IMAGE = "floci/floci:latest";
    public static final int FLOCI_PORT = 4566;
    public static final String ACCESS_KEY = "test";
    public static final String SECRET_KEY = "test";
    public static final String REGION = "us-east-1";

    public FlociContainer(DockerImageName image) {
        super(image);
    }

    public static FlociContainer create() {
        return new FlociContainer(DockerImageName.parse(IMAGE))
                .withExposedPorts(FLOCI_PORT)
                .withEnv("FLOCI_DEFAULT_REGION", REGION)
                .waitingFor(Wait.forHttp("/_floci/health")
                        .forPort(FLOCI_PORT)
                        .withStartupTimeout(java.time.Duration.ofSeconds(60)));
    }

    /** Endpoint URL suitable for {@code S3Client.builder().endpointOverride(URI.create(...))}. */
    public String endpointUrl() {
        return "http://" + getHost() + ":" + getMappedPort(FLOCI_PORT);
    }

    /** Convenience alias used by S3-flavoured tests. Same single endpoint as everything else. */
    public String s3Endpoint() {
        return endpointUrl();
    }

    public String getEndpoint() { return endpointUrl(); }
    public String getRegion()    { return REGION; }
    public String getAccessKey() { return ACCESS_KEY; }
    public String getSecretKey() { return SECRET_KEY; }
}
