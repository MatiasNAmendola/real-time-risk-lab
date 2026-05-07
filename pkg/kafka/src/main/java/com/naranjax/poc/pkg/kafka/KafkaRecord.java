package com.naranjax.poc.pkg.kafka;

import java.util.Map;

/**
 * Typed wrapper for a Kafka message including headers and partition metadata.
 *
 * @param <T> deserialized payload type
 */
public record KafkaRecord<T>(
        String topic,
        int partition,
        long offset,
        String key,
        T value,
        Map<String, String> headers
) {}
