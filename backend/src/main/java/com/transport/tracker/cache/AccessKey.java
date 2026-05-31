package com.transport.tracker.cache;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable key for ConcurrentSkipListMap-based LRU index.
 * Provides natural ordering by lastAccessedAt timestamp, with tie-breaking using sequence number.
 * 
 * Thread-safety: Immutable and thread-safe.
 * Ordering: Primary by timestamp (oldest first), secondary by sequence (FIFO for same timestamp).
 * 
 * @author Senior Java Architect
 * @since 1.0
 */
public final class AccessKey implements Comparable<AccessKey> {
    
    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);
    
    private final long lastAccessedAtMillis;
    private final Object cacheKey;
    private final long sequence;
    
    /**
     * Creates a new AccessKey with the given timestamp and cache key.
     * 
     * @param lastAccessedAtMillis timestamp in milliseconds since epoch
     * @param cacheKey the cache key this AccessKey references (must not be null)
     * @throws NullPointerException if cacheKey is null
     */
    public AccessKey(long lastAccessedAtMillis, Object cacheKey) {
        this.lastAccessedAtMillis = lastAccessedAtMillis;
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
    }
    
    public long getLastAccessedAtMillis() {
        return lastAccessedAtMillis;
    }
    
    public Object getCacheKey() {
        return cacheKey;
    }
    
    public long getSequence() {
        return sequence;
    }
    
    /**
     * Compares AccessKeys for natural ordering in ConcurrentSkipListMap.
     * 
     * Ordering rules:
     * 1. Primary: lastAccessedAtMillis (ascending - oldest first)
     * 2. Secondary: sequence number (ascending - FIFO for same timestamp)
     * 
     * This ensures:
     * - firstEntry() returns the least recently accessed entry
     * - Entries with identical timestamps are ordered by insertion order
     * - No collisions in the skip list (sequence guarantees uniqueness)
     * 
     * @param other the AccessKey to compare to
     * @return negative if this < other, zero if equal, positive if this > other
     */
    @Override
    public int compareTo(AccessKey other) {
        // Primary ordering: timestamp (oldest first)
        int timestampComparison = Long.compare(this.lastAccessedAtMillis, other.lastAccessedAtMillis);
        if (timestampComparison != 0) {
            return timestampComparison;
        }
        
        // Secondary ordering: sequence (FIFO for same timestamp)
        return Long.compare(this.sequence, other.sequence);
    }
    
    /**
     * Equality based on timestamp, cacheKey, and sequence.
     * Required for correct removal from ConcurrentSkipListMap.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessKey)) return false;
        AccessKey that = (AccessKey) o;
        return lastAccessedAtMillis == that.lastAccessedAtMillis &&
               sequence == that.sequence &&
               Objects.equals(cacheKey, that.cacheKey);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(lastAccessedAtMillis, cacheKey, sequence);
    }
    
    @Override
    public String toString() {
        return "AccessKey{" +
               "timestamp=" + lastAccessedAtMillis +
               ", key=" + cacheKey +
               ", seq=" + sequence +
               '}';
    }
}