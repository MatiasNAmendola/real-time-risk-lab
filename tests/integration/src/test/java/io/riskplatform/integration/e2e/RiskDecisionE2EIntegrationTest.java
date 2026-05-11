package io.riskplatform.integration.e2e;

import io.riskplatform.integration.IntegrationTestSupport;
import io.riskplatform.integration.containers.FlociContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test that exercises Postgres + Redpanda + Floci S3 together
 * (ADR-0042).
 *
 * Flow:
 *  1. A APPROVE/DECLINE decision is persisted to decision_audit in Postgres.
 *  2. An outbox event for that decision is published to the risk-decisions Redpanda topic.
 *  3. An audit JSON is stored to the Floci risk-audit bucket.
 *  4. All three sides are asserted.
 */
@Testcontainers
class RiskDecisionE2EIntegrationTest extends IntegrationTestSupport {

    private static final String TOPIC = "risk-decisions-e2e";
    private static final String BUCKET = "risk-audit-e2e";

    @Container
    static final PostgreSQLContainer<?> PG = postgres;

    @Container
    static final RedpandaContainer REDPANDA = redpanda;

    @Container
    static final FlociContainer FLOCI = floci;

    private static S3Client s3;

    @BeforeAll
    static void setup() throws Exception {
        // Postgres schema
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS decision_audit (
                        id           UUID PRIMARY KEY,
                        request_id   UUID        NOT NULL,
                        decision     VARCHAR(20) NOT NULL,
                        amount       NUMERIC(18,2),
                        recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
        }

        // S3 client + bucket (Floci, ADR-0042)
        s3 = S3Client.builder()
                .endpointOverride(URI.create(FLOCI.s3Endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(FlociContainer.ACCESS_KEY, FlociContainer.SECRET_KEY)))
                .forcePathStyle(true)
                .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    void approve_decision_persisted_published_and_audited() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String decisionId = UUID.randomUUID().toString();
        String decision = "APPROVE";
        double amount = 15_000.00;

        // Step 1: persist decision to Postgres
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT INTO decision_audit(id, request_id, decision, amount) VALUES (?::uuid, ?::uuid, ?, ?)")) {
            ins.setString(1, decisionId);
            ins.setString(2, requestId);
            ins.setString(3, decision);
            ins.setDouble(4, amount);
            ins.executeUpdate();
        }

        // Step 2: publish outbox event to Redpanda
        String payload = String.format("{\"decisionId\":\"%s\",\"requestId\":\"%s\",\"decision\":\"%s\",\"amount\":%.2f}",
                decisionId, requestId, decision, amount);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(TOPIC, decisionId, payload)).get();
            producer.flush();
        }

        // Step 3: write audit JSON to Floci S3
        String s3Key = "2026/05/07/" + decisionId + ".json";
        s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(s3Key).contentType("application/json").build(),
                RequestBody.fromString(payload, StandardCharsets.UTF_8));

        // Assertions — side 1: Postgres
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             PreparedStatement sel = conn.prepareStatement(
                     "SELECT decision, amount FROM decision_audit WHERE id = ?::uuid")) {
            sel.setString(1, decisionId);
            ResultSet rs = sel.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("decision")).isEqualTo("APPROVE");
            assertThat(rs.getDouble("amount")).isEqualTo(15_000.00);
        }

        // Assertions — side 2: Redpanda
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<String> receivedValues = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && receivedValues.isEmpty()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> {
                    if (decisionId.equals(r.key())) receivedValues.add(r.value());
                });
            }
        }

        assertThat(receivedValues).hasSize(1);
        assertThat(receivedValues.get(0)).contains("APPROVE").contains(requestId);

        // Assertions — side 3: Floci S3
        byte[] auditBytes = s3.getObject(
                GetObjectRequest.builder().bucket(BUCKET).key(s3Key).build(),
                ResponseTransformer.toBytes()).asByteArray();
        String auditContent = new String(auditBytes, StandardCharsets.UTF_8);

        assertThat(auditContent).isEqualTo(payload);
        assertThat(auditContent).contains("APPROVE");
        assertThat(auditContent).contains(decisionId);
    }
}
