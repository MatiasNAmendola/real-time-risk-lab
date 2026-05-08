package io.riskplatform.rules.client.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.rules.client.RiskClientException;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.sdks.riskevents.DecisionEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Kafka channel — encapsulates topic names, broker addresses, and serialization.
 */
public final class EventsClient {

    static final String DECISIONS_TOPIC      = "risk-decisions";
    static final String CUSTOM_EVENTS_TOPIC  = "risk-custom-events";

    private final ClientConfig config;
    private final ObjectMapper mapper;

    public EventsClient(ClientConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    /**
     * Starts a consumer loop for the risk-decisions topic.
     * Blocks the calling thread; run in a background thread / virtual thread.
     */
    public void consumeDecisions(String groupId, Consumer<DecisionEvent> handler) {
        Properties props = consumerProps(groupId);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(DECISIONS_TOPIC));
            while (!Thread.currentThread().isInterrupted()) {
                var records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, String> rec : records) {
                    try {
                        DecisionEvent event = mapper.readValue(rec.value(), DecisionEvent.class);
                        handler.accept(event);
                    } catch (Exception e) {
                        // skip unparseable record
                    }
                }
            }
        }
    }

    /**
     * Publishes a custom event envelope to the risk-custom-events topic.
     * The envelope is serialized as JSON.
     */
    public void publishCustomEvent(Map<String, Object> envelope) {
        Properties props = producerProps();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String value = mapper.writeValueAsString(envelope);
            producer.send(new ProducerRecord<>(CUSTOM_EVENTS_TOPIC, value)).get();
        } catch (Exception e) {
            throw new RiskClientException("Failed to publish custom event", e);
        }
    }

    // -----------------------------------------------------------------------

    private Properties consumerProps(String groupId) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.environment().kafkaBroker());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return p;
    }

    private Properties producerProps() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.environment().kafkaBroker());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "1");
        return p;
    }
}
