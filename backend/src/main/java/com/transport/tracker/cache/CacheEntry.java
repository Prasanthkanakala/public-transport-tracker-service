package com.transport.tracker.cache;

import lombok.Getter;

import java.time.Instant;

/**
* Wrapper that stores a cached value along with its creation timestamp and TTL.
* Supports both strict expiry (isExpired) and extended stale window (isStale).
*
* @param <V> The type of the cached value
*/
@Getter
public class CacheEntry<V> {

    private final V value;
    private final Instant createdAt;
    private final long ttlSeconds;
    private volatile Instant lastAccessedAt;

    public CacheEntry(V value, long ttlSeconds) {
        this.value = value;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Returns true if the entry has exceeded its normal TTL.
     * Expired entries are not returned on normal GET – only on getStale().
     */
    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }

    /**
     * Returns true if the entry has exceeded the extended stale window.
     * Stale entries are evicted permanently by the background cleaner.
     *
     * @param staleTtlSeconds The maximum age before a stale entry is discarded
     */
    public boolean isStaleFor(long staleTtlSeconds) {
        return Instant.now().isAfter(createdAt.plusSeconds(staleTtlSeconds));
    }

    /**
     * Returns how many seconds old this entry is.
     */
    public long getAgeSeconds() {
        return Instant.now().getEpochSecond() - createdAt.getEpochSecond();
    }

    public void updateLastAccess() {
        this.lastAccessedAt = Instant.now();
    }
}
 