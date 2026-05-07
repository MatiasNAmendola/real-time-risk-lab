package com.naranjax.integration;

import com.naranjax.integration.containers.MinioContainer;
import com.naranjax.integration.containers.MotoContainer;
import com.naranjax.integration.containers.OpenBaoContainer;
import com.naranjax.integration.containers.ValkeyContainer;
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
 * Example subclass:
 * <pre>{@code
 * @Testcontainers
 * class MyTest extends IntegrationTestSupport {
 *
 *     @Container
 *     static final PostgreSQLContainer<?> PG = postgres;
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
            com.naranjax.integration.containers.PostgresContainer.create();

    protected static final RedpandaContainer redpanda =
            com.naranjax.integration.containers.RedpandaContainer.create();

    protected static final ValkeyContainer valkey =
            ValkeyContainer.create();

    protected static final MinioContainer minio =
            MinioContainer.create();

    protected static final MotoContainer moto =
            MotoContainer.create();

    protected static final OpenBaoContainer openBao =
            OpenBaoContainer.create();
}
