package com.naranjax.integration.kafka;

import com.naranjax.integration.IntegrationTestSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Redpanda correctly preserves message content and headers
 * across a producer/consumer cycle.
 */
@Testcontainers
class KafkaPublishConsumeIntegrationTest extends IntegrationTestSupport {

    private static final String TOPIC = "trace-events";
    private static final int MESSAGE_COUNT = 5;

    @Container
    static final RedpandaContainer REDPANDA = redpanda;

    @Test
    void producer_publishes_five_messages_with_traceparent_headers_and_consumer_receives_all() throws Exception {
        String groupId = "kafka-test-group-" + UUID.randomUUID();

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        List<String> sentKeys = new ArrayList<>();
        List<String> sentTraceparents = new ArrayList<>();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String key = UUID.randomUUID().toString();
                String traceparent = "00-" + UUID.randomUUID().toString().replace("-", "") + "-" + String.format("%016x", i + 1) + "-01";
                String value = "{\"seq\":" + i + ",\"data\":\"payload-" + i + "\"}";

                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, value);
                record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));

                producer.send(record).get();
                sentKeys.add(key);
                sentTraceparents.add(traceparent);
            }
            producer.flush();
        }

        // Consumer reads from the beginning
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<ConsumerRecord<String, String>> received = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 15_000;
            while (received.size() < MESSAGE_COUNT && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(received::add);
            }
        }

        assertThat(received).hasSize(MESSAGE_COUNT);

        for (ConsumerRecord<String, String> record : received) {
            assertThat(record.value()).contains("payload");

            Header traceparentHeader = record.headers().lastHeader("traceparent");
            assertThat(traceparentHeader)
                    .as("traceparent header must be present on record key=%s", record.key())
                    .isNotNull();

            String traceparentValue = new String(traceparentHeader.value(), StandardCharsets.UTF_8);
            assertThat(traceparentValue).startsWith("00-");

            // Confirm this key was one we sent
            assertThat(sentKeys).contains(record.key());

            // Confirm traceparent matches what was sent for this key
            int idx = sentKeys.indexOf(record.key());
            assertThat(traceparentValue).isEqualTo(sentTraceparents.get(idx));
        }
    }
}
