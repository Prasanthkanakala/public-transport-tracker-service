package com.transport.tracker.cache;

import com.transport.tracker.model.TransportData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
* Spring-managed service that wraps {@link InMemoryCache} and exposes
* cache operations for TransportData objects.
*
* Cache key format: "{city}:{routeId}" (both lower-cased, "all" if absent)
*/
@Slf4j
@Service
public class CacheService {

    private final InMemoryCache<String, TransportData> dataCache;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public CacheService(
            @Value("${transit.cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${transit.cache.stale-ttl-seconds:3600}") long staleTtlSeconds,
            @Value("${transit.cache.max-size:1000}") int maxSize) {
        this.dataCache = new InMemoryCache<>(ttlSeconds, staleTtlSeconds, maxSize);
        log.info("CacheService initialised: TTL={}s, staleTTL={}s, maxSize={}",
                ttlSeconds, staleTtlSeconds, maxSize);
    }

    // ─── Core cache operations ─────────────────────────────────────────────────

    public void put(String key, TransportData data) {
        dataCache.put(key, data);
        log.debug("Cached transport data for key: {}", key);
    }

    /** Returns fresh cached data if within TTL. */
    public Optional<TransportData> get(String key) {
        return dataCache.get(key);
    }

    /**
     * Returns stale (expired-but-recent) data.
     * Used as a fallback when upstream APIs are unavailable.
     */
    public Optional<TransportData> getStale(String key) {
        Optional<TransportData> stale = dataCache.getStale(key);
        if (stale.isPresent()) {
            log.warn("Serving STALE cache data for key: {}. Live API unavailable.", key);
        }
        return stale;
    }

    /** Returns the age in seconds of the cached entry, if present. */
    public Optional<Long> getCacheAge(String key) {
        return dataCache.getEntry(key).map(CacheEntry::getAgeSeconds);
    }

    public void invalidate(String key) {
        dataCache.invalidate(key);
        log.debug("Invalidated cache for key: {}", key);
    }

    public void clear() {
        dataCache.clear();
        log.info("Cache cleared");
    }

    public boolean containsValidKey(String key) {
        return dataCache.containsValidKey(key);
    }

    public <T> T withKeyLock(String key, Supplier<T> action) {
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                return action.get();
            } finally {
                keyLocks.remove(key, lock);
            }
        }
    }

    /** Returns the current size of the cache. */
    public int size() {
        return dataCache.getStats().size();
    }

    // ─── Key building ──────────────────────────────────────────────────────────

    public String buildKey(String city, String routeId) {
        String c = (city != null && !city.isBlank()) ? city.toLowerCase().strip() : "global";
        String r = (routeId != null && !routeId.isBlank()) ? routeId.toLowerCase().strip() : "all";
        return c + ":" + r;
    }

    // ─── Stats & monitoring ────────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        InMemoryCache.CacheStats stats = dataCache.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("size", stats.size());
        result.put("hits", stats.hits());
        result.put("misses", stats.misses());
        result.put("staleHits", stats.staleHits());
        result.put("evictions", stats.evictions());
        result.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
        result.put("ttlSeconds", stats.ttlSeconds());
        result.put("staleTtlSeconds", stats.staleTtlSeconds());
        return result;
    }

    /** Periodically logs cache stats to help with observability. */
    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void logCacheStats() {
        InMemoryCache.CacheStats stats = dataCache.getStats();
        log.info("Cache stats: size={}, hits={}, misses={}, staleHits={}, hitRate={:.2f}%",
                stats.size(), stats.hits(), stats.misses(), stats.staleHits(),
                stats.hitRate() * 100);
    }
}


 
