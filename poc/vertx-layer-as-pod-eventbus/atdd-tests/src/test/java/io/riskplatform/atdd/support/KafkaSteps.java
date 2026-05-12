package io.riskplatform.atdd.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Reusable Kafka helper called from Karate feature files via Java interop.
 *
 * <p>Usage in a .feature:
 * <pre>
 *   * def KafkaSteps = Java.type('io.riskplatform.atdd.support.KafkaSteps')
 *   * def records = KafkaSteps.consume(kafkaBroker, kafkaTopic, 1, 5000)
 *   * def event = records[0]
 *   * match event.decision == 'DECLINE'
 * </pre>
 */
public class KafkaSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KafkaSteps() {}

    /**
     * Creates an ephemeral consumer, subscribes to {@code topic}, and polls until
     * {@code expectedCount} records are received or {@code timeoutMs} elapses.
     *
     * @param broker       bootstrap server, e.g. {@code localhost:9092}
     * @param topic        Kafka topic name
     * @param expectedCount number of records to wait for
     * @param timeoutMs    maximum total wait in milliseconds
     * @return list of parsed JSON objects (each record value deserialized as {@code Map<String,Object>})
     * @throws AssertionError if fewer than {@code expectedCount} records arrive within the timeout
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> consume(
            String broker, String topic, int expectedCount, long timeoutMs) {

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "atdd-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(expectedCount * 2));

        List<Map<String, Object>> results = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            while (results.size() < expectedCount && System.currentTimeMillis() < deadline) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(Math.min(remaining, 1000)));
                records.forEach(r -> {
                    try {
                        Map<String, Object> parsed = MAPPER.readValue(r.value(), Map.class);
                        results.add(parsed);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Kafka record: " + r.value(), e);
                    }
                });
            }
        }

        if (results.size() < expectedCount) {
            throw new AssertionError(
                    String.format("Expected %d records from topic '%s' within %dms but got %d",
                            expectedCount, topic, timeoutMs, results.size()));
        }

        return results;
    }

    /**
     * Convenience overload using default timeout of 5 000 ms.
     */
    public static List<Map<String, Object>> consume(String broker, String topic, int expectedCount) {
        return consume(broker, topic, expectedCount, 5_000);
    }
}
