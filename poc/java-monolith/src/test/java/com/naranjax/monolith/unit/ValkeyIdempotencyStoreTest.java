package com.naranjax.monolith.unit;

import com.naranjax.monolith.repository.ValkeyIdempotencyStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ValkeyIdempotencyStore in fallback (in-memory) mode.
 * Validates put-if-absent semantics without a live Redis/Valkey instance.
 */
class ValkeyIdempotencyStoreTest {

    // Default constructor falls back to in-memory when VALKEY_URL is not set.
    private final ValkeyIdempotencyStore store = new ValkeyIdempotencyStore();

    @Test
    void get_returnsNull_forUnknownKey() {
        assertThat(store.get("no-such-key")).isNull();
    }

    @Test
    void putIfAbsent_storesValue() {
        store.putIfAbsent("key-1", "{\"decision\":\"APPROVE\"}");
        assertThat(store.get("key-1")).isEqualTo("{\"decision\":\"APPROVE\"}");
    }

    @Test
    void putIfAbsent_doesNotOverwrite_existingValue() {
        store.putIfAbsent("key-2", "first-value");
        store.putIfAbsent("key-2", "second-value");
        assertThat(store.get("key-2")).isEqualTo("first-value");
    }

    @Test
    void putIfAbsent_allowsDifferentKeys() {
        store.putIfAbsent("key-a", "value-a");
        store.putIfAbsent("key-b", "value-b");
        assertThat(store.get("key-a")).isEqualTo("value-a");
        assertThat(store.get("key-b")).isEqualTo("value-b");
    }

    @Test
    void get_returnsNull_forUnknownKey_afterPutOnDifferentKey() {
        store.putIfAbsent("key-x", "value");
        assertThat(store.get("key-y")).isNull();
    }
}
