package com.transport.tracker.cache;

import java.time.Instant;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Represents a cache entry scheduled for expiration in the DelayQueue.
 * Implements Delayed interface to support priority-based expiration.
 * 
 * CONCURRENCY SAFETY:
 * ───────────────────
 * Each ExpiryNode carries a version timestamp that matches the CacheEntry it represents.
 * This prevents the race condition where:
 *   1. put(key) at T0 → creates ExpiryNode v1
 *   2. put(key) at T1 → creates ExpiryNode v2 (new value)
 *   3. ExpiryNode v1 expires at T2 → must NOT remove the new value
 * 
 * The eviction thread compares versions before removing entries.
 * 
 * @param <K> Cache key type
 */
public class ExpiryNode<K> implements Delayed {

    private final K key;
    private final Instant expiryTime;
    private final Instant version; // Creation timestamp used for version checking

    /**
     * Creates an expiry node for a cache entry.
     * 
     * @param key The cache key
     * @param expiryTime When this entry should expire
     * @param version The creation timestamp of the associated CacheEntry (for version matching)
     */
    public ExpiryNode(K key, Instant expiryTime, Instant version) {
        this.key = key;
        this.expiryTime = expiryTime;
        this.version = version;
    }

    public K getKey() {
        return key;
    }

    public Instant getVersion() {
        return version;
    }

    /**
     * Returns the remaining delay before this node should be processed.
     * Negative values indicate the delay has already elapsed.
     */
    @Override
    public long getDelay(TimeUnit unit) {
        long delayMillis = expiryTime.toEpochMilli() - Instant.now().toEpochMilli();
        return unit.convert(delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Compares this node with another for ordering in the DelayQueue.
     * Earlier expiry times have higher priority.
     */
    @Override
    public int compareTo(Delayed other) {
        if (this == other) {
            return 0;
        }
        
        long diff = this.getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
        
        if (diff < 0) {
            return -1;
        } else if (diff > 0) {
            return 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "ExpiryNode{" +
                "key=" + key +
                ", expiryTime=" + expiryTime +
                ", version=" + version +
                '}';
    }
}
