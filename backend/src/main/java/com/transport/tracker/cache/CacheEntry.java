package com.transport.tracker.cache;

import lombok.Getter;

import java.time.Instant;

/**
* Wrapper that stores a cached value along with its creation timestamp and TTL.
* Supports both strict expiry (isExpired) and extended stale window (isStale).
*
* VERSION TRACKING:
* ─────────────────
* The createdAt timestamp serves as a version identifier to prevent race conditions
* during concurrent updates. When an ExpiryNode fires, it compares its version with
* the current CacheEntry's createdAt to ensure it doesn't remove a newer entry.
*
* @param <V> The type of the cached value
*/
@Getter
public class CacheEntry<V> {

    private final V value;
    private final Instant createdAt; // Also serves as version identifier
    private final long ttlSeconds;
    private volatile Instant lastAccessedAt;
    
    // LRU index tracking: stores the current AccessKey for this entry in the skip list
    // Volatile ensures visibility across threads; updated on every access
    private volatile AccessKey currentAccessKey;

    public CacheEntry(V value, long ttlSeconds) {
        this.value = value;
        this.createdAt = Instant.now();
        this.ttlSeconds = ttlSeconds;
        this.lastAccessedAt = Instant.now();
        this.currentAccessKey = null; // Set by cache after construction
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

    /**
     * Updates the last access timestamp.
     * Note: currentAccessKey must be updated separately by the cache implementation
     * to maintain LRU index consistency.
     */
    public void updateLastAccess() {
        this.lastAccessedAt = Instant.now();
    }
    
    /**
     * Sets the current AccessKey for this entry in the LRU index.
     * Used by cache implementation to track the entry's position in ConcurrentSkipListMap.
     * 
     * @param accessKey the AccessKey currently representing this entry in the LRU index
     */
    public void setCurrentAccessKey(AccessKey accessKey) {
        this.currentAccessKey = accessKey;
    }

    /**
     * Returns the version identifier (creation timestamp) for this entry.
     * Used by DelayQueue eviction to verify entry hasn't been replaced.
     */
    public Instant getVersion() {
        return createdAt;
    }
    
    /**
     * Returns the current AccessKey for this entry in the LRU index.
     * May be null if entry hasn't been registered in the index yet.
     * 
     * @return the current AccessKey, or null if not yet indexed
     */
    public AccessKey getCurrentAccessKey() {
        return currentAccessKey;
    }
}
 