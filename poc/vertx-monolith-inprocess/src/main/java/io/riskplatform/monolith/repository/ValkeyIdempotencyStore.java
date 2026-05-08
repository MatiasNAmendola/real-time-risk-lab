package io.riskplatform.monolith.repository;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency store backed by Valkey (Redis-compatible) via Lettuce.
 *
 * <p>Uses SET NX EX semantics: keys expire after 24 hours to prevent unbounded growth.
 * Falls back to an in-memory ConcurrentHashMap when the Valkey URL is not configured.
 *
 * <p>Env var: VALKEY_URL (e.g. redis://valkey:6379)
 */
public class ValkeyIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(ValkeyIdempotencyStore.class);
    private static final long TTL_SECONDS = 86_400L; // 24 hours

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final boolean redisEnabled;
    private final ConcurrentHashMap<String, String> fallbackStore = new ConcurrentHashMap<>();

    public ValkeyIdempotencyStore() {
        String valkeyUrl = System.getenv().getOrDefault("VALKEY_URL", "");
        boolean enabled  = !valkeyUrl.isBlank();
        RedisClient client = null;
        StatefulRedisConnection<String, String> conn = null;

        if (enabled) {
            try {
                client = RedisClient.create(valkeyUrl);
                conn   = client.connect();
                log.info("[monolith] ValkeyIdempotencyStore connected: {}", valkeyUrl);
            } catch (Exception e) {
                log.warn("[monolith] Valkey connection failed ({}), using in-memory fallback", e.getMessage());
                enabled = false;
                if (client != null) { try { client.shutdown(); } catch (Exception ignored) {} }
                client = null;
                conn   = null;
            }
        } else {
            log.info("[monolith] VALKEY_URL not set — using in-memory idempotency store");
        }

        this.redisClient  = client;
        this.connection   = conn;
        this.redisEnabled = enabled;
    }

    /** For testing: construct with explicit URL. */
    public ValkeyIdempotencyStore(String redisUrl) {
        this.redisClient  = RedisClient.create(redisUrl);
        this.connection   = redisClient.connect();
        this.redisEnabled = true;
        log.info("[monolith] ValkeyIdempotencyStore test-constructor: {}", redisUrl);
    }

    /**
     * Returns the stored decision for the given key, or null if not found.
     * Blocks — call from a worker thread or executeBlocking.
     */
    public String get(String key) {
        if (!redisEnabled) return fallbackStore.get(key);
        try {
            RedisCommands<String, String> cmd = connection.sync();
            return cmd.get("idempotency:" + key);
        } catch (Exception e) {
            log.warn("[monolith] Valkey GET failed: {}", e.getMessage());
            return fallbackStore.get(key);
        }
    }

    /**
     * Stores the decision under the key if no value exists yet (SET NX EX).
     * Blocks — call from a worker thread or executeBlocking.
     */
    public void putIfAbsent(String key, String decision) {
        if (!redisEnabled) {
            fallbackStore.putIfAbsent(key, decision);
            return;
        }
        try {
            RedisCommands<String, String> cmd = connection.sync();
            cmd.set("idempotency:" + key, decision, SetArgs.Builder.nx().ex(TTL_SECONDS));
        } catch (Exception e) {
            log.warn("[monolith] Valkey SET NX failed: {}", e.getMessage());
            fallbackStore.putIfAbsent(key, decision);
        }
    }
}
