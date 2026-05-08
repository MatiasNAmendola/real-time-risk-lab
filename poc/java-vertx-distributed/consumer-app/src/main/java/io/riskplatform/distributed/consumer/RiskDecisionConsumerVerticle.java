package io.riskplatform.distributed.consumer;

import io.riskplatform.distributed.consumer.aws.ConsumerS3AuditPublisher;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Standalone Vert.x verticle that consumes `risk-decisions` from Redpanda/Kafka.
 * Logs structured MDC fields so the OTel agent can correlate log → trace.
 *
 * The `traceparent` Kafka header is reconstructed and put into MDC so that any
 * downstream log analysis tool (e.g. OpenObserve) can join this log line to the
 * originating trace produced by usecase-app.
 */
public class RiskDecisionConsumerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionConsumerVerticle.class);
    private static final String TOPIC = "risk-decisions";
    private static final String GROUP_ID = "consumer-app";

    private KafkaConsumer<String, String> consumer;
    private ConsumerS3AuditPublisher s3AuditPublisher;

    @Override
    public void start(Promise<Void> startPromise) {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers",
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "redpanda:9092"));
        config.put("key.deserializer",   "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("group.id",           GROUP_ID);
        config.put("auto.offset.reset",  "earliest");
        config.put("enable.auto.commit", "true");

        // AWS SDK client construction performs network I/O and region/STS lookups
        // that can block the event loop for seconds. Off-load to a worker thread
        // and only subscribe to Kafka after init completes, so the first record
        // never lands on a null publisher.
        vertx.<Void>executeBlocking(() -> {
            long t0 = System.currentTimeMillis();
            s3AuditPublisher = new ConsumerS3AuditPublisher(vertx);
            log.info("[consumer-app] AWS SDK init complete (off event loop) in {} ms",
                System.currentTimeMillis() - t0);
            return null;
        }, false)
            .compose(v -> {
                consumer = KafkaConsumer.create(vertx, config);
                consumer.handler(this::handleRecord);
                consumer.exceptionHandler(err ->
                    log.error("[consumer-app] Kafka consumer error: {}", err.getMessage(), err));
                return consumer.subscribe(TOPIC);
            })
            .onSuccess(v -> {
                log.info("[consumer-app] Subscribed to topic={} group={}", TOPIC, GROUP_ID);
                startPromise.complete();
            })
            .onFailure(err -> {
                log.error("[consumer-app] start failed: {}", err.getMessage(), err);
                startPromise.fail(err);
            });
    }

    private void handleRecord(KafkaConsumerRecord<String, String> record) {
        // Extract OTel traceparent from Kafka header so OTel agent/log bridge can link this log to the trace
        String traceparent   = headerValue(record, "traceparent");
        String correlationId = headerValue(record, "correlationId");
        String idempotencyKey = headerValue(record, "idempotencyKey");

        MDC.put("correlationId",   correlationId != null ? correlationId : "");
        MDC.put("traceparent",     traceparent   != null ? traceparent   : "");
        MDC.put("idempotencyKey",  idempotencyKey != null ? idempotencyKey : "");
        MDC.put("kafka.topic",     record.topic());
        MDC.put("kafka.partition", String.valueOf(record.partition()));
        MDC.put("kafka.offset",    String.valueOf(record.offset()));

        try {
            JsonObject payload = new JsonObject(record.value());
            String decision      = payload.getString("decision", "UNKNOWN");
            String transactionId = payload.getString("transactionId", "");
            String occurredAt    = payload.getString("occurredAt", "");

            log.info("[consumer-app] risk-decision consumed: decision={} transactionId={} occurredAt={} traceparent={}",
                decision, transactionId, occurredAt, traceparent);

            // Publish DECLINE/REVIEW to S3 audit log (MinIO) — fire-and-forget
            s3AuditPublisher.publishIfHighRisk(decision, transactionId, correlationId, occurredAt, traceparent)
                .onFailure(err -> log.error("[consumer-app] S3 high-risk audit failed: {}", err.getMessage()));

        } catch (Exception e) {
            log.warn("[consumer-app] Failed to parse record value: {} | raw={}", e.getMessage(), record.value());
        } finally {
            MDC.clear();
        }
    }

    private String headerValue(KafkaConsumerRecord<String, String> record, String headerName) {
        for (io.vertx.kafka.client.producer.KafkaHeader h : record.headers()) {
            if (headerName.equals(h.key())) {
                return h.value().toString();
            }
        }
        return null;
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (consumer != null) {
            consumer.close().onComplete(ar -> stopPromise.complete());
        } else {
            stopPromise.complete();
        }
    }
}
