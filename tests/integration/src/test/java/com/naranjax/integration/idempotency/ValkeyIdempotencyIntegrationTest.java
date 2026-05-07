package com.naranjax.integration.idempotency;

import com.naranjax.integration.IntegrationTestSupport;
import com.naranjax.integration.containers.ValkeyContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the idempotency key pattern using SET NX (set-if-not-exists) in Valkey.
 *
 * Scenarios:
 *  1. First SET NX on a new key returns OK.
 *  2. Second SET NX on the same key returns null (already taken).
 *  3. After the TTL expires, a new SET NX on the same key returns OK again.
 */
@Testcontainers
class ValkeyIdempotencyIntegrationTest extends IntegrationTestSupport {

    @Container
    static final ValkeyContainer VALKEY = valkey;

    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;

    @BeforeAll
    static void connectToValkey() {
        redisClient = RedisClient.create(VALKEY.getRedisUrl());
        connection = redisClient.connect();
    }

    @AfterAll
    static void closeConnection() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @Test
    void first_write_on_new_key_returns_ok() {
        String key = "idempotency:" + UUID.randomUUID();
        RedisCommands<String, String> cmds = connection.sync();

        String result = cmds.set(key, "processed", SetArgs.Builder.nx().ex(60));

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void second_write_on_same_key_returns_null() {
        String key = "idempotency:" + UUID.randomUUID();
        RedisCommands<String, String> cmds = connection.sync();

        cmds.set(key, "processed", SetArgs.Builder.nx().ex(60));
        String secondResult = cmds.set(key, "processed-again", SetArgs.Builder.nx().ex(60));

        assertThat(secondResult)
                .as("SET NX on existing key must return null, not overwrite")
                .isNull();

        // Original value must still be intact
        assertThat(cmds.get(key)).isEqualTo("processed");
    }

    @Test
    void after_ttl_expires_same_key_can_be_set_again() throws InterruptedException {
        String key = "idempotency:short-ttl:" + UUID.randomUUID();
        RedisCommands<String, String> cmds = connection.sync();

        // Set with 1-second TTL
        String first = cmds.set(key, "first-value", SetArgs.Builder.nx().ex(1));
        assertThat(first).isEqualTo("OK");

        // Wait for TTL to expire
        TimeUnit.SECONDS.sleep(2);

        assertThat(cmds.get(key)).isNull();

        String afterExpiry = cmds.set(key, "second-value", SetArgs.Builder.nx().ex(60));
        assertThat(afterExpiry)
                .as("SET NX after TTL expiry must succeed with OK")
                .isEqualTo("OK");

        assertThat(cmds.get(key)).isEqualTo("second-value");
    }
}
