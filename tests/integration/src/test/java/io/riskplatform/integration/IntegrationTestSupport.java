package io.riskplatform.integration;

import io.riskplatform.integration.containers.FlociContainer;
import io.riskplatform.integration.containers.ValkeyContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * Base class for all integration tests.
 *
 * Container strategy: each static container is declared here but only started when the
 * concrete subclass references it via {@code @Container}.  Because the fields are static
 * and the JUnit Jupiter Testcontainers extension honours static lifecycle, a container is
 * started once per JVM and reused across all test classes that share it — provided you
 * also enable {@code testcontainers.reuse.enable=true} in {@code ~/.testcontainers.properties}.
 *
 * Opt-in pattern: subclasses annotate only the fields they need with {@code @Container}
 * so unneeded containers are never started.
 *
 * <p>AWS mocks: a single {@link FlociContainer} (ADR-0042) covers S3 + SQS + SNS +
 * Secrets Manager + KMS + STS + IAM. Use {@link #floci} from subclasses (also exposed
 * via the legacy aliases {@code minio} and {@code moto} for source compatibility during
 * the migration window — both resolve to the same container).
 *
 * Example subclass:
 * <pre>{@code
 * @Testcontainers
 * class MyTest extends IntegrationTestSupport {
 *
 *     @Container
 *     static final PostgreSQLContainer<?> PG = postgres;
 *
 *     @Container
 *     static final FlociContainer AWS = floci;
 *
 *     @Test
 *     void something() { ... }
 * }
 * }</pre>
 */
@Testcontainers
public abstract class IntegrationTestSupport {

    // Pre-configured container instances — not yet started; subclasses drive lifecycle.

    protected static final PostgreSQLContainer<?> postgres =
            io.riskplatform.integration.containers.PostgresContainer.create();

    protected static final RedpandaContainer redpanda =
            io.riskplatform.integration.containers.RedpandaContainer.create();

    protected static final ValkeyContainer valkey =
            ValkeyContainer.create();

    /** Unified AWS emulator — replaces MinIO + ElasticMQ + Moto + OpenBao (ADR-0042). */
    protected static final FlociContainer floci =
            FlociContainer.create();

    /**
     * Legacy alias for {@link #floci}. New tests should use {@code floci} directly.
     * Kept so existing tests that reference {@code MINIO} continue to compile during
     * the migration window. Both names refer to the same single Floci container.
     */
    protected static final FlociContainer minio = floci;

    /**
     * Legacy alias for {@link #floci}. New tests should use {@code floci} directly.
     */
    protected static final FlociContainer moto = floci;
}
