package com.transport.tracker.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
* Thread-safe in-memory cache with TTL-based expiration.
*
* DESIGN RATIONALE (Option A – Resilience):
* ─────────────────────────────────────────
* This class is the backbone of our degradation strategy:
*
*   1. get(key)       → Returns fresh data if within TTL
*   2. getStale(key)  → Returns expired (but not too old) data when live API fails.
*                       This is the "serve stale on upstream failure" mechanism.
*   3. Background eviction removes entries that exceed the stale TTL window,
*      preventing unbounded memory growth.
*
* No third-party cache library (Ehcache, Caffeine, Redis) is used.
* Pure Java: ConcurrentHashMap + ScheduledExecutorService.
*
* @param <K> Cache key type
* @param <V> Cache value type
*/
public class InMemoryCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();

    private final long ttlSeconds;
    private final long staleTtlSeconds;
    private final int maxSize;

    private final ScheduledExecutorService evictionScheduler;
    private final Object evictionLock = new Object();

    // Metrics counters
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private final AtomicInteger missCount = new AtomicInteger(0);
    private final AtomicInteger staleHitCount = new AtomicInteger(0);
    private final AtomicInteger evictionCount = new AtomicInteger(0);

    public InMemoryCache(long ttlSeconds, long staleTtlSeconds, int maxSize) {
        this.ttlSeconds = ttlSeconds;
        this.staleTtlSeconds = staleTtlSeconds;
        this.maxSize = maxSize;

        // Background eviction daemon – clears stale entries on an interval
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-eviction-thread");
            t.setDaemon(true);
            return t;
        });
        this.evictionScheduler.scheduleAtFixedRate(
                this::evictStaleEntries,
                ttlSeconds,
                ttlSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Stores a value in the cache. If cache is at max capacity, the
     * least-recently-accessed entry is evicted first (approximate LRU).
     */
    public void put(K key, V value) {
        synchronized (evictionLock) {
            if (store.size() >= maxSize) {
                evictLeastRecentlyAccessed();
            }
            store.put(key, new CacheEntry<>(value, ttlSeconds));
        }
    }

    /**
     * Returns a fresh (non-expired) cached value, or empty if absent/expired.
     */
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        if (entry.isExpired()) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        entry.updateLastAccess();
        hitCount.incrementAndGet();
        return Optional.of(entry.getValue());
    }

    /**
     * Returns stale data (expired but within staleTtlSeconds window).
     * Called only when the live API fetch has failed.
     * This is the core resilience mechanism for Option A.
     */
    public Optional<V> getStale(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isStaleFor(staleTtlSeconds)) {
            store.remove(key);
            return Optional.empty();
        }
        // Entry is expired but still within the stale window – serve it
        staleHitCount.incrementAndGet();
        return Optional.of(entry.getValue());
    }

    /**
     * Returns the raw CacheEntry for inspection (e.g., age calculation).
     */
    public Optional<CacheEntry<V>> getEntry(K key) {
        return Optional.ofNullable(store.get(key));
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void clear() {
        synchronized (evictionLock) {
            store.clear();
        }
    }

    public int size() {
        return store.size();
    }

    public boolean containsValidKey(K key) {
        CacheEntry<V> entry = store.get(key);
        return entry != null && !entry.isExpired();
    }

    public CacheStats getStats() {
        return new CacheStats(
                store.size(),
                hitCount.get(),
                missCount.get(),
                staleHitCount.get(),
                evictionCount.get(),
                ttlSeconds,
                staleTtlSeconds
        );
    }

    public void shutdown() {
        evictionScheduler.shutdownNow();
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private void evictStaleEntries() {
        synchronized (evictionLock) {
            int before = store.size();
            store.entrySet().removeIf(e -> e.getValue().isStaleFor(staleTtlSeconds));
            evictionCount.addAndGet(before - store.size());
        }
    }

    private void evictLeastRecentlyAccessed() {
        store.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                        (a, b) -> a.getLastAccessedAt().compareTo(b.getLastAccessedAt())
                ))
                .ifPresent(e -> {
                    store.remove(e.getKey());
                    evictionCount.incrementAndGet();
                });
    }

    // ─── Stats DTO ─────────────────────────────────────────────────────────────

    public record CacheStats(
            int size,
            int hits,
            int misses,
            int staleHits,
            int evictions,
            long ttlSeconds,
            long staleTtlSeconds
    ) {
        public double hitRate() {
            int total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }
}
 