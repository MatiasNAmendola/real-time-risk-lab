package io.riskplatform.integration.containers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Tansu (ADR-0043) Kafka-wire-compatible broker for integration tests.
 *
 * <p>Replaces the previous {@code RedpandaContainer} after the repo-wide
 * Redpanda → Tansu migration. The container uses Tansu's in-memory storage
 * backend ({@code STORAGE_ENGINE=memory://tansu/}) so the test does NOT need
 * Floci/S3 alongside it — keeping per-test container count down.
 *
 * <p>Port binding: Tansu's advertised listener is a static env var, so the
 * advertised host:port MUST be reachable from the test JVM and identical to
 * the bootstrap address. We bind the host port to a fixed value (9092) and
 * advertise {@code tcp://localhost:9092}. Side-effect: only one Tansu test
 * container can run at a time on a given host — fine for JUnit sequential
 * lifecycle; parallel-class execution would clash.
 *
 * <p>Compat note: librdkafka 2.x clients (kcat 1.7.x) fail Tansu's
 * ApiVersionRequest handshake, and franz-go consumer groups hit upstream
 * Fetch hangs (tansu-io/tansu#668). The JVM Apache Kafka 7.x client used
 * by these integration tests is verified to work.
 */
public final class TansuContainer extends GenericContainer<TansuContainer> {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("ghcr.io/tansu-io/tansu:0.6.0");

    private static final int KAFKA_PORT = 9092;

    @SuppressWarnings("resource")
    public TansuContainer() {
        super(IMAGE);
        // Fixed host port: advertised listener URL must match what clients dial.
        addFixedExposedPort(KAFKA_PORT, KAFKA_PORT);
        withEnv("CLUSTER_ID", "tansu");
        withEnv("ADVERTISED_LISTENER_URL", "tcp://localhost:" + KAFKA_PORT);
        withEnv("STORAGE_ENGINE", "memory://tansu/");
        withEnv("AWS_ALLOW_HTTP", "true");
        withEnv("RUST_LOG", "warn,tansu_broker=info");
        waitingFor(Wait.forListeningPort());
    }

    public static TansuContainer create() {
        return new TansuContainer();
    }

    /** Bootstrap servers string usable by an Apache Kafka client on the test host. */
    public String getBootstrapServers() {
        return "localhost:" + KAFKA_PORT;
    }

    /**
     * Creates a topic via AdminClient. Tansu 0.6.0 has no auto-create-topics
     * server flag, so tests that previously relied on Redpanda's auto-create
     * default must invoke this from {@code @BeforeAll}. Idempotent: silently
     * swallows TopicExistsException.
     */
    public void createTopic(String topic, int partitions) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(new NewTopic(topic, partitions, (short) 1)))
                    .all()
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            // TopicExistsException is wrapped; tolerate either nested form.
            String msg = e.getMessage() == null ? "" : e.getMessage();
            Throwable cause = e.getCause();
            String causeMsg = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
            if (!msg.contains("TopicExists") && !causeMsg.contains("TopicExists")
                    && !msg.contains("already exists") && !causeMsg.contains("already exists")) {
                throw new RuntimeException("Failed to create topic " + topic, e);
            }
        }
    }
}
