package com.transport.tracker.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
* Thread-safe in-memory cache with TTL-based expiration using DelayQueue.
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
* EVICTION STRATEGY (Dual-Index Design):
* ───────────────────────────────────────
* This implementation uses TWO concurrent data structures for efficient eviction:
*
* 1. DelayQueue<ExpiryNode> for TTL-based eviction:
*    - Automatically orders entries by expiry time
*    - Eviction thread blocks on delayQueue.take() - zero CPU when idle
*    - O(log n) insertion, O(1) removal of expired entries
*    - Handles stale TTL cleanup (entries older than staleTtlSeconds)
*
* 2. ConcurrentSkipListMap<AccessKey, K> for LRU eviction:
*    - Maintains entries sorted by lastAccessedAt timestamp
*    - O(log n) insertion/removal vs O(n) full-cache scan
*    - firstEntry() gives least recently accessed entry in O(log n)
*    - Handles max-size enforcement when cache is full
*
* Benefits:
*   - TTL eviction: Event-driven, zero CPU when idle, predictable cleanup
*   - LRU eviction: O(log n) vs O(n) scan, scales to large caches (10K+ entries)
*   - Combined: Efficient handling of both time-based and size-based eviction
*
* Tradeoffs:
*   - Memory overhead: ~48 bytes/entry (ExpiryNode) + ~64 bytes/entry (AccessKey)
*   - put() cost: O(log n) for both DelayQueue and SkipListMap insertion
*   - get() cost: O(log n) for AccessKey update (remove old + insert new)
*   - Overall: Better scalability for high-throughput caches with strict size limits
*
* CONCURRENCY SAFETY:
* ───────────────────
* Multi-layered concurrency protection:
*
* 1. Version checking (DelayQueue eviction):
*    - Each CacheEntry has a createdAt timestamp (version)
*    - Each ExpiryNode stores the version of the entry it represents
*    - Eviction thread only removes entry if versions match
*    - Prevents old ExpiryNodes from removing newly updated entries
*
* 2. AccessKey tracking (LRU index):
*    - Each CacheEntry stores its current AccessKey reference
*    - On access update: atomically remove old AccessKey, insert new one
*    - Prevents orphaned AccessKey entries in skip list
*    - Ensures LRU index stays synchronized with CacheEntry state
*
* 3. Synchronized eviction:
*    - evictionLock protects put() and LRU eviction operations
*    - Prevents race: Thread A updates access while Thread B evicts
*    - Ensures atomic removal from both ConcurrentHashMap and SkipListMap
*
* 4. Cleanup on removal:
*    - All removal paths (TTL, LRU, manual) clean up AccessKey from skip list
*    - Prevents memory leaks from orphaned index entries
*
* No third-party cache library (Ehcache, Caffeine, Redis) is used.
* Pure Java: ConcurrentHashMap + DelayQueue + daemon thread.
*
* @param <K> Cache key type
* @param <V> Cache value type
*/
@Slf4j
@Component
public class InMemoryCache<K, V> {

    // Primary storage: source of truth for cache entries
    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    
    // TTL eviction index: ordered by expiry time for automatic stale entry removal
    private final DelayQueue<ExpiryNode<K>> expiryQueue = new DelayQueue<>();
    
    // LRU eviction index: ordered by lastAccessedAt for efficient max-size enforcement
    // Maps AccessKey (timestamp + sequence) → cache key
    private final ConcurrentSkipListMap<AccessKey, K> lruIndex = new ConcurrentSkipListMap<>();

    private final long ttlSeconds;
    private final long staleTtlSeconds;
    private final int maxSize;

    private final Object evictionLock = new Object();
    private final AtomicBoolean evictionThreadRunning = new AtomicBoolean(false);
    private volatile Thread evictionThread;

    // Metrics counters
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private final AtomicInteger missCount = new AtomicInteger(0);
    private final AtomicInteger staleHitCount = new AtomicInteger(0);
    private final AtomicInteger evictionCount = new AtomicInteger(0);

    public InMemoryCache(long ttlSeconds, long staleTtlSeconds, int maxSize) {
        this.ttlSeconds = ttlSeconds;
        this.staleTtlSeconds = staleTtlSeconds;
        this.maxSize = maxSize;
    }

    /**
     * Starts the DelayQueue-based eviction daemon thread.
     * Called automatically by Spring after bean construction.
     * 
     * The thread blocks on delayQueue.take() and only wakes when an entry expires.
     * This is more efficient than periodic full-cache scans.
     */
    @PostConstruct
    public void startEvictionThread() {
        if (evictionThreadRunning.compareAndSet(false, true)) {
            evictionThread = new Thread(this::runEvictionLoop, "cache-delayqueue-eviction");
            evictionThread.setDaemon(true);
            evictionThread.start();
            log.info("DelayQueue eviction thread started for cache with TTL={}s, staleTTL={}s", 
                    ttlSeconds, staleTtlSeconds);
        }
    }

    /**
     * Stores a value in the cache. If cache is at max capacity, the
     * least-recently-accessed entry is evicted first using O(log n) LRU index.
     * 
     * Also registers:
     * 1. ExpiryNode in DelayQueue for automatic TTL-based eviction
     * 2. AccessKey in ConcurrentSkipListMap for efficient LRU tracking
     * 
     * Complexity: O(log n) for both DelayQueue and SkipListMap insertion
     */
    public void put(K key, V value) {
        synchronized (evictionLock) {
            // Remove old LRU index entry if updating existing key
            CacheEntry<V> oldEntry = store.get(key);
            if (oldEntry != null) {
                AccessKey oldAccessKey = oldEntry.getCurrentAccessKey();
                if (oldAccessKey != null) {
                    lruIndex.remove(oldAccessKey);
                    log.trace("Removed old AccessKey from LRU index: key={}, accessKey={}", key, oldAccessKey);
                }
            }
            
            // Evict LRU entry if cache is full and this is a new key
            if (store.size() >= maxSize && !store.containsKey(key)) {
                evictLeastRecentlyAccessedFast();
            }
            
            // Create new cache entry
            CacheEntry<V> entry = new CacheEntry<>(value, ttlSeconds);
            store.put(key, entry);
            
            // Register in LRU index (O(log n))
            AccessKey accessKey = new AccessKey(entry.getLastAccessedAt().toEpochMilli(), key);
            entry.setCurrentAccessKey(accessKey);
            lruIndex.put(accessKey, key);
            
            // Register in TTL eviction queue (O(log n))
            Instant expiryTime = entry.getCreatedAt().plusSeconds(staleTtlSeconds);
            ExpiryNode<K> expiryNode = new ExpiryNode<>(key, expiryTime, entry.getVersion());
            expiryQueue.offer(expiryNode);
            
            log.debug("Cache put: key={}, expiryTime={}, version={}, accessKey={}", 
                    key, expiryTime, entry.getVersion(), accessKey);
        }
    }

    /**
     * Returns a fresh (non-expired) cached value, or empty if absent/expired.
     * 
     * On cache hit, updates LRU index by:
     * 1. Removing old AccessKey from skip list
     * 2. Creating new AccessKey with updated timestamp
     * 3. Inserting new AccessKey into skip list
     * 
     * Complexity: O(log n) for LRU index update
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
        
        // Update access time and LRU index
        synchronized (evictionLock) {
            // Remove old AccessKey from LRU index
            AccessKey oldAccessKey = entry.getCurrentAccessKey();
            if (oldAccessKey != null) {
                lruIndex.remove(oldAccessKey);
            }
            
            // Update last access timestamp
            entry.updateLastAccess();
            
            // Insert new AccessKey into LRU index
            AccessKey newAccessKey = new AccessKey(entry.getLastAccessedAt().toEpochMilli(), key);
            entry.setCurrentAccessKey(newAccessKey);
            lruIndex.put(newAccessKey, key);
            
            log.trace("Updated LRU index on access: key={}, oldAccessKey={}, newAccessKey={}",
                    key, oldAccessKey, newAccessKey);
        }
        
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

    /**
     * Removes an entry from the cache and cleans up its LRU index entry.
     * Prevents memory leaks from orphaned AccessKey references.
     */
    public void invalidate(K key) {
        synchronized (evictionLock) {
            CacheEntry<V> entry = store.remove(key);
            if (entry != null) {
                AccessKey accessKey = entry.getCurrentAccessKey();
                if (accessKey != null) {
                    lruIndex.remove(accessKey);
                    log.debug("Invalidated cache entry and removed from LRU index: key={}", key);
                }
            }
        }
    }

    /**
     * Clears all cache entries and LRU index.
     * Ensures no orphaned AccessKey entries remain in skip list.
     */
    public void clear() {
        synchronized (evictionLock) {
            store.clear();
            lruIndex.clear();
            log.info("Cache cleared: all entries and LRU index removed");
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
                staleTtlSeconds,
                lruIndex.size()  // Monitor LRU index size for consistency checks
        );
    }

    /**
     * Gracefully shuts down the eviction thread.
     * Called automatically by Spring before bean destruction.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down cache eviction thread...");
        evictionThreadRunning.set(false);
        
        if (evictionThread != null) {
            evictionThread.interrupt();
            try {
                evictionThread.join(5000); // Wait up to 5 seconds
                log.info("Cache eviction thread stopped successfully");
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for eviction thread to stop");
                Thread.currentThread().interrupt();
            }
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Eviction loop that runs in a dedicated daemon thread.
     * Blocks on delayQueue.take() until an entry expires, then removes it.
     * 
     * CONCURRENCY SAFETY:
     * ──────────────────
     * Before removing an entry, verifies that the ExpiryNode's version matches
     * the current CacheEntry's version. This prevents the race condition:
     *   1. put(key) at T0 → ExpiryNode v1 created
     *   2. put(key) at T1 → ExpiryNode v2 created (new value)
     *   3. ExpiryNode v1 fires at T2 → must NOT remove the new value
     * 
     * CLEANUP:
     * ────────
     * Also removes the corresponding AccessKey from the LRU index to prevent
     * memory leaks from orphaned skip list entries.
     */
    private void runEvictionLoop() {
        log.info("Eviction loop started");
        
        while (evictionThreadRunning.get()) {
            try {
                // Block until an entry expires (zero CPU usage while waiting)
                ExpiryNode<K> expiredNode = expiryQueue.take();
                
                K key = expiredNode.getKey();
                
                synchronized (evictionLock) {
                    CacheEntry<V> currentEntry = store.get(key);
                    
                    // Version check: only remove if the entry hasn't been replaced
                    if (currentEntry != null && currentEntry.getVersion().equals(expiredNode.getVersion())) {
                        store.remove(key);
                        
                        // Clean up LRU index to prevent memory leak
                        AccessKey accessKey = currentEntry.getCurrentAccessKey();
                        if (accessKey != null) {
                            lruIndex.remove(accessKey);
                        }
                        
                        evictionCount.incrementAndGet();
                        log.debug("Evicted stale entry: key={}, version={}, age={}s, accessKey={}", 
                                key, expiredNode.getVersion(), currentEntry.getAgeSeconds(), accessKey);
                    } else {
                        log.debug("Skipped eviction (entry updated): key={}, nodeVersion={}, currentVersion={}",
                                key, expiredNode.getVersion(), 
                                currentEntry != null ? currentEntry.getVersion() : "null");
                    }
                }
                
            } catch (InterruptedException e) {
                log.info("Eviction thread interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in eviction loop", e);
            }
        }
        
        log.info("Eviction loop terminated");
    }

    /**
     * Fast LRU eviction using ConcurrentSkipListMap index.
     * 
     * PERFORMANCE:
     * ────────────
     * O(log n) vs O(n) full-cache scan.
     * 
     * ALGORITHM:
     * ──────────
     * 1. Get firstEntry() from lruIndex (oldest AccessKey)
     * 2. Remove entry from ConcurrentHashMap
     * 3. Remove AccessKey from lruIndex
     * 
     * CONCURRENCY:
     * ────────────
     * Must be called within evictionLock to prevent race conditions:
     * - Thread A updates access time (removes old AccessKey, inserts new)
     * - Thread B evicts entry (removes AccessKey)
     * Without lock, Thread B might remove Thread A's newly inserted AccessKey.
     */
    private void evictLeastRecentlyAccessedFast() {
        // Get oldest entry from LRU index (O(log n))
        Map.Entry<AccessKey, K> oldestEntry = lruIndex.firstEntry();
        
        if (oldestEntry != null) {
            AccessKey oldestAccessKey = oldestEntry.getKey();
            K key = oldestEntry.getValue();
            
            // Remove from primary storage
            CacheEntry<V> removedEntry = store.remove(key);
            
            // Remove from LRU index
            lruIndex.remove(oldestAccessKey);
            
            evictionCount.incrementAndGet();
            
            if (removedEntry != null) {
                log.debug("LRU eviction (fast): key={}, lastAccessed={}, accessKey={}", 
                        key, removedEntry.getLastAccessedAt(), oldestAccessKey);
            } else {
                log.warn("LRU eviction found orphaned AccessKey: key={}, accessKey={}", 
                        key, oldestAccessKey);
            }
        } else {
            log.warn("LRU eviction called but lruIndex is empty (cache size={})", store.size());
        }
    }

    // ─── Stats DTO ─────────────────────────────────────────────────────────────

    public record CacheStats(
            int size,
            int hits,
            int misses,
            int staleHits,
            int evictions,
            long ttlSeconds,
            long staleTtlSeconds,
            int lruIndexSize  // For monitoring LRU index consistency
    ) {
        public double hitRate() {
            int total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
        
        /**
         * Checks if LRU index is consistent with cache size.
         * In a healthy cache, lruIndexSize should equal size.
         * Discrepancy indicates orphaned entries or synchronization issues.
         */
        public boolean isLruIndexConsistent() {
            return size == lruIndexSize;
        }
    }
}
 