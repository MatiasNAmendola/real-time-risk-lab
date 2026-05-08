package io.riskplatform.poc.pkg.kafka;

/**
 * Port for deduplicating Kafka messages by idempotency key.
 * Implementations may back this with Redis, Postgres, or an in-memory store.
 */
public interface KafkaIdempotencyStore {
    /** Returns true if the key has already been processed (duplicate). */
    boolean isDuplicate(String idempotencyKey);

    /** Marks the key as processed. Must be called inside the same transaction as message handling. */
    void markProcessed(String idempotencyKey);
}
