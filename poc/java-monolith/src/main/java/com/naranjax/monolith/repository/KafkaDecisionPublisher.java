package com.naranjax.monolith.repository;

import com.naranjax.distributed.shared.RiskDecision;
import com.naranjax.distributed.shared.RiskRequest;
import io.opentelemetry.api.trace.Span;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Publishes risk decisions to Redpanda (Kafka-compatible) via kafka-clients.
 *
 * <p>Uses the synchronous kafka-clients producer wrapped in Vert.x executeBlocking
 * from the call site to avoid blocking the event loop.
 *
 * <p>Env var: KAFKA_BOOTSTRAP_SERVERS (default: redpanda:9092)
 */
public class KafkaDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDecisionPublisher.class);

    private final KafkaProducer<String, String> producer;
    private final boolean enabled;

    public KafkaDecisionPublisher() {
        String brokers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "");
        boolean kafkaEnabled = !brokers.isBlank();
        KafkaProducer<String, String> p = null;

        if (kafkaEnabled) {
            try {
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.ACKS_CONFIG, "1");
                props.put(ProducerConfig.CLIENT_ID_CONFIG, "java-monolith-producer");
                p = new KafkaProducer<>(props);
                log.info("[monolith] KafkaDecisionPublisher connected: {}", brokers);
            } catch (Exception e) {
                log.warn("[monolith] Kafka producer init failed: {}", e.getMessage());
                kafkaEnabled = false;
            }
        } else {
            log.info("[monolith] KAFKA_BOOTSTRAP_SERVERS not set — Kafka publish disabled");
        }

        this.producer = p;
        this.enabled  = kafkaEnabled;
    }

    /**
     * Publishes a risk decision event to the specified Kafka topic.
     * Fire-and-forget: errors are logged but not propagated.
     * BLOCKS — wrap in executeBlocking if calling from a verticle event loop.
     */
    public void publish(RiskRequest req, RiskDecision decision, String correlationId, String topic) {
        if (!enabled || producer == null) return;

        String eventId = UUID.randomUUID().toString();
        Span span = Span.current();
        String traceId    = span.getSpanContext().getTraceId();
        String spanId     = span.getSpanContext().getSpanId();
        String traceparent = "00-" + traceId + "-" + spanId + "-01";

        String payload = "{"
            + "\"eventId\":\"" + eventId + "\","
            + "\"eventVersion\":1,"
            + "\"correlationId\":\"" + correlationId + "\","
            + "\"occurredAt\":\"" + Instant.now() + "\","
            + "\"transactionId\":\"" + req.transactionId() + "\","
            + "\"decision\":\"" + decision.decision() + "\","
            + "\"reason\":\"" + decision.reason() + "\""
            + "}";

        ProducerRecord<String, String> record =
            new ProducerRecord<>(topic, req.transactionId(), payload);
        record.headers()
            .add("traceparent",    traceparent.getBytes())
            .add("correlationId",  correlationId.getBytes())
            .add("idempotencyKey", eventId.getBytes());

        producer.send(record, (meta, err) -> {
            if (err != null) {
                log.error("[monolith] Kafka publish failed: {}", err.getMessage());
            } else {
                log.info("[monolith] Kafka published topic={} partition={} offset={}",
                    meta.topic(), meta.partition(), meta.offset());
            }
        });
    }
}
