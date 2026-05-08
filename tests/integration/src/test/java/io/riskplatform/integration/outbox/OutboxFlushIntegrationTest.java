package io.riskplatform.integration.outbox;

import io.riskplatform.integration.IntegrationTestSupport;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the outbox pattern end-to-end:
 *  1. An PENDING event is inserted into the outbox_event table (Postgres).
 *  2. An inline OutboxRelay reads PENDING rows, publishes to Redpanda, marks PUBLISHED.
 *  3. A Kafka consumer confirms the message arrived on the topic.
 *  4. The Postgres row shows status = PUBLISHED.
 */
@Testcontainers
class OutboxFlushIntegrationTest extends IntegrationTestSupport {

    private static final String TOPIC = "risk-decisions";

    @Container
    static final PostgreSQLContainer<?> PG = postgres;

    @Container
    static final RedpandaContainer REDPANDA = redpanda;

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS outbox_event (
                        id         UUID PRIMARY KEY,
                        payload    TEXT        NOT NULL,
                        status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        attempts   INT         NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    @Test
    void outbox_relay_publishes_to_redpanda_and_marks_published() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"decision\":\"APPROVE\",\"requestId\":\"" + eventId + "\"}";

        // 1. Insert a PENDING outbox event
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             PreparedStatement insert = conn.prepareStatement(
                     "INSERT INTO outbox_event(id, payload, status) VALUES (?::uuid, ?, 'PENDING')")) {
            insert.setString(1, eventId);
            insert.setString(2, payload);
            insert.executeUpdate();
        }

        // 2. Inline OutboxRelay: read PENDING -> publish -> mark PUBLISHED
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            ResultSet pending = conn.createStatement().executeQuery(
                    "SELECT id, payload FROM outbox_event WHERE status = 'PENDING'");

            while (pending.next()) {
                String id = pending.getString("id");
                String msg = pending.getString("payload");

                producer.send(new ProducerRecord<>(TOPIC, id, msg)).get();

                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE outbox_event SET status = 'PUBLISHED', attempts = attempts + 1 WHERE id = ?::uuid")) {
                    update.setString(1, id);
                    update.executeUpdate();
                }
            }
            producer.flush();
        }

        // 3. Consumer reads the message from Redpanda
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        String receivedPayload = null;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 15_000;
            outer:
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (var record : records) {
                    if (eventId.equals(record.key())) {
                        receivedPayload = record.value();
                        break outer;
                    }
                }
            }
        }

        assertThat(receivedPayload)
                .as("Message must be received on Redpanda topic")
                .isNotNull()
                .contains("APPROVE");

        // 4. Postgres row must be PUBLISHED
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             PreparedStatement sel = conn.prepareStatement(
                     "SELECT status FROM outbox_event WHERE id = ?::uuid")) {
            sel.setString(1, eventId);
            ResultSet rs = sel.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("PUBLISHED");
        }
    }
}
