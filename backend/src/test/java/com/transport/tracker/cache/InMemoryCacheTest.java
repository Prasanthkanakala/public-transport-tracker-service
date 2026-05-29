package com.transport.tracker.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
* Unit tests for InMemoryCache.
*
* Tests verify:
* - put / get operations
* - TTL expiry (entries not returned after TTL)
* - Stale data window (entries returned after TTL but before stale window)
* - Max size enforcement
* - Cache stats accuracy
*/
@DisplayName("InMemoryCache")
class InMemoryCacheTest {

    // Short TTL for testing
    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCache<>(2L, 10L, 100); // 2s TTL, 10s stale, 100 max
    }

    @Test
    @DisplayName("Should return value immediately after put")
    void shouldReturnValueAfterPut() {
        cache.put("key1", "value1");
        Optional<String> result = cache.get("key1");
        assertThat(result).isPresent().contains("value1");
    }

    @Test
    @DisplayName("Should return empty when key not present")
    void shouldReturnEmptyForMissingKey() {
        Optional<String> result = cache.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty after TTL expiry")
    void shouldExpireAfterTtl() throws InterruptedException {
        cache.put("key2", "value2");
        Thread.sleep(2500); // Wait past TTL
        Optional<String> result = cache.get("key2");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return stale data after TTL but within stale window")
    void shouldReturnStaleDataAfterTtl() throws InterruptedException {
        cache.put("key3", "value3");
        Thread.sleep(2500); // Past TTL, still within stale window

        Optional<String> fresh = cache.get("key3");
        Optional<String> stale = cache.getStale("key3");

        assertThat(fresh).isEmpty();
        assertThat(stale).isPresent().contains("value3");
    }

    @Test
    @DisplayName("Should return empty for stale after staleTtl exceeded")
    void shouldReturnEmptyWhenStaleTtlExceeded() throws InterruptedException {
        InMemoryCache<String, String> shortStaleCache = new InMemoryCache<>(1L, 2L, 100);
        shortStaleCache.put("key4", "value4");
        Thread.sleep(2500); // Past both TTL and stale window

        Optional<String> stale = shortStaleCache.getStale("key4");
        assertThat(stale).isEmpty();
    }

    @Test
    @DisplayName("Should invalidate specific key")
    void shouldInvalidateKey() {
        cache.put("key5", "value5");
        cache.invalidate("key5");
        assertThat(cache.get("key5")).isEmpty();
    }

    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAll() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        cache.clear();

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("Should track cache stats accurately")
    void shouldTrackStats() {
        cache.put("stats-key", "stats-value");

        cache.get("stats-key");  // hit
        cache.get("stats-key");  // hit
        cache.get("missing-key"); // miss

        InMemoryCache.CacheStats stats = cache.getStats();
        assertThat(stats.hits()).isEqualTo(2);
        assertThat(stats.misses()).isEqualTo(1);
        assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0);
    }

    @Test
    @DisplayName("Should not exceed max size")
    void shouldEnforceMaxSize() {
        InMemoryCache<String, String> smallCache = new InMemoryCache<>(60L, 300L, 3);
        smallCache.put("a", "1");
        smallCache.put("b", "2");
        smallCache.put("c", "3");
        smallCache.put("d", "4"); // triggers eviction

        assertThat(smallCache.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("containsValidKey should return false for expired entries")
    void containsValidKeyShouldReturnFalseForExpired() throws InterruptedException {
        cache.put("check-key", "check-value");
        assertThat(cache.containsValidKey("check-key")).isTrue();

        Thread.sleep(2500);
        assertThat(cache.containsValidKey("check-key")).isFalse();
    }
}