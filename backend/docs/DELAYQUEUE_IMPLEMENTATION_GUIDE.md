# DelayQueue-Based Cache Eviction Implementation Guide

## Overview

This document explains the refactoring of the Spring Boot in-memory cache from periodic full-cache scans to DelayQueue-based TTL eviction.

---

## Architecture Changes

### Before: ScheduledExecutorService Approach

```
┌─────────────────────────────────────────┐
│  ScheduledExecutorService               │
│  (runs every TTL interval)              │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  evictStaleEntries()                    │
│  • Scans ENTIRE cache                   │
│  • removeIf(entry.isStale())            │
│  • O(n) time complexity                 │
│  • Runs even when no entries expire     │
└─────────────────────────────────────────┘
```

**Problems:**
- **O(n) scan every interval** - CPU waste when cache is large
- **Fixed interval overhead** - Runs even when nothing expires
- **Delayed eviction** - Stale entries remain until next scan
- **CPU usage constant** - No idle state

---

### After: DelayQueue Approach

```
┌─────────────────────────────────────────┐
│  put(key, value)                        │
│  1. Create CacheEntry (with version)   │
│  2. store.put(key, entry)               │
│  3. Create ExpiryNode                   │
│  4. expiryQueue.offer(node)             │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  DelayQueue<ExpiryNode>                 │
│  • Internally uses PriorityQueue        │
│  • Ordered by expiry time               │
│  • O(log n) insertion                   │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  Eviction Daemon Thread                 │
│  while (running) {                      │
│    node = expiryQueue.take()  // BLOCKS│
│    if (versionMatches(node))            │
│      store.remove(node.key)             │
│  }                                      │
└─────────────────────────────────────────┘
```

**Benefits:**
- **Event-driven eviction** - Only wakes when entry expires
- **Zero CPU when idle** - Thread blocks on `take()`
- **Immediate eviction** - Entries removed exactly at stale TTL
- **O(log n) per put** - Eliminates O(n) periodic scans

---

## Time Complexity Analysis

| Operation | Before (ScheduledExecutor) | After (DelayQueue) |
|-----------|---------------------------|--------------------|
| **put()** | O(1) | O(log n) |
| **get()** | O(1) | O(1) |
| **Eviction (per interval)** | O(n) full scan | O(1) per expired entry |
| **Total eviction cost (k entries)** | O(n) every interval | O(k) only when entries expire |
| **CPU when idle** | Constant (periodic wakeup) | Zero (blocked on take()) |

**Example Scenario:**
- Cache size: 10,000 entries
- TTL interval: 300 seconds
- Entries expiring per interval: ~50

**Before:**
- Every 300s: Scan all 10,000 entries → O(10,000)
- CPU usage: Constant every 5 minutes

**After:**
- Per put: O(log 10,000) ≈ 13 comparisons
- Eviction: 50 × O(1) removals when they expire
- CPU usage: Zero between expirations

**Verdict:** DelayQueue is more efficient for caches with:
- Large number of entries (>1000)
- Long TTL intervals (>60s)
- Low expiration rate relative to cache size

---

## Concurrency Safety: Version Checking

### The Race Condition Problem

```
Timeline:

T0: put("user:123", valueA)
    ├─> CacheEntry created (version = T0)
    └─> ExpiryNode created (version = T0, expires at T0 + 3600s)

T1: put("user:123", valueB)  // Same key updated!
    ├─> CacheEntry created (version = T1)
    └─> ExpiryNode created (version = T1, expires at T1 + 3600s)

T2: ExpiryNode(version=T0) fires
    ├─> Without version check: Removes valueB ❌ BUG!
    └─> With version check: Skips removal ✅ SAFE!
```

### Solution: Version Matching

**CacheEntry.java:**
```java
public class CacheEntry<V> {
    private final Instant createdAt; // Serves as version identifier
    
    public Instant getVersion() {
        return createdAt;
    }
}
```

**ExpiryNode.java:**
```java
public class ExpiryNode<K> implements Delayed {
    private final K key;
    private final Instant expiryTime;
    private final Instant version; // Matches CacheEntry.createdAt
    
    public ExpiryNode(K key, Instant expiryTime, Instant version) {
        this.key = key;
        this.expiryTime = expiryTime;
        this.version = version;
    }
}
```

**Eviction Thread:**
```java
private void runEvictionLoop() {
    while (evictionThreadRunning.get()) {
        ExpiryNode<K> expiredNode = expiryQueue.take();
        CacheEntry<V> currentEntry = store.get(expiredNode.getKey());
        
        // CRITICAL: Only remove if versions match
        if (currentEntry != null && 
            currentEntry.getVersion().equals(expiredNode.getVersion())) {
            store.remove(expiredNode.getKey());
            evictionCount.incrementAndGet();
        } else {
            // Entry was updated - skip eviction
            log.debug("Skipped eviction (entry updated): key={}", key);
        }
    }
}
```

**Why This Works:**
- Each `put()` creates a new `CacheEntry` with a new `createdAt` timestamp
- The `ExpiryNode` captures the version at creation time
- When the old `ExpiryNode` fires, its version won't match the new entry's version
- The eviction is safely skipped

---

## Memory Tradeoffs

### Additional Memory per Cache Entry

**ExpiryNode overhead:**
```
Object header:     16 bytes
K key reference:    8 bytes
Instant expiryTime: 16 bytes (2 longs)
Instant version:    16 bytes (2 longs)
─────────────────────────────
Total per node:    ~56 bytes
```

**For 10,000 entries:**
- ExpiryNode overhead: 56 × 10,000 = 560 KB
- CacheEntry data: Depends on V size
- Total overhead: ~0.5 MB for 10K entries

**Verdict:** Negligible for most applications. The CPU savings far outweigh the memory cost.

---

## Thread Lifecycle Management

### Startup: @PostConstruct

```java
@PostConstruct
public void startEvictionThread() {
    if (evictionThreadRunning.compareAndSet(false, true)) {
        evictionThread = new Thread(this::runEvictionLoop, "cache-delayqueue-eviction");
        evictionThread.setDaemon(true); // Won't prevent JVM shutdown
        evictionThread.start();
        log.info("DelayQueue eviction thread started");
    }
}
```

**Why @PostConstruct:**
- Ensures thread starts after Spring bean is fully initialized
- Runs automatically during application startup
- Follows Spring lifecycle best practices

### Shutdown: @PreDestroy

```java
@PreDestroy
public void shutdown() {
    log.info("Shutting down cache eviction thread...");
    evictionThreadRunning.set(false);
    
    if (evictionThread != null) {
        evictionThread.interrupt(); // Wake from take() block
        try {
            evictionThread.join(5000); // Wait up to 5 seconds
            log.info("Cache eviction thread stopped successfully");
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for eviction thread to stop");
            Thread.currentThread().interrupt();
        }
    }
}
```

**Why @PreDestroy:**
- Ensures graceful shutdown before Spring destroys the bean
- Prevents thread leaks during application shutdown
- Allows in-flight evictions to complete

---

## Preserved Functionality

### ✅ Fresh TTL Behavior

```java
public Optional<V> get(K key) {
    CacheEntry<V> entry = store.get(key);
    if (entry == null || entry.isExpired()) {
        missCount.incrementAndGet();
        return Optional.empty();
    }
    entry.updateLastAccess();
    hitCount.incrementAndGet();
    return Optional.of(entry.getValue());
}
```

**Unchanged:** Still returns empty if entry exceeds `ttlSeconds`.

### ✅ Stale Cache Fallback

```java
public Optional<V> getStale(K key) {
    CacheEntry<V> entry = store.get(key);
    if (entry == null || entry.isStaleFor(staleTtlSeconds)) {
        return Optional.empty();
    }
    staleHitCount.incrementAndGet();
    return Optional.of(entry.getValue());
}
```

**Unchanged:** Still serves expired data within `staleTtlSeconds` window.

### ✅ Cache Metrics

```java
public CacheStats getStats() {
    return new CacheStats(
        store.size(),
        hitCount.get(),
        missCount.get(),
        staleHitCount.get(),
        evictionCount.get(), // Now includes DelayQueue evictions
        ttlSeconds,
        staleTtlSeconds
    );
}
```

**Unchanged:** All metrics preserved, `evictionCount` now tracks DelayQueue evictions.

### ✅ LRU Eviction

```java
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
```

**Unchanged:** Still O(n) scan - see optional enhancement below.

---

## Testing Recommendations

### Unit Tests to Preserve

```java
@Test
void shouldReturnFreshValue() {
    cache.put("key", "value");
    assertThat(cache.get("key")).isPresent();
}

@Test
void shouldReturnEmptyAfterTTL() throws InterruptedException {
    cache.put("key", "value");
    Thread.sleep((ttlSeconds + 1) * 1000);
    assertThat(cache.get("key")).isEmpty();
}

@Test
void shouldReturnStaleValueWhenExpired() throws InterruptedException {
    cache.put("key", "value");
    Thread.sleep((ttlSeconds + 1) * 1000);
    assertThat(cache.getStale("key")).isPresent();
}

@Test
void shouldEvictStaleEntries() throws InterruptedException {
    cache.put("key", "value");
    Thread.sleep((staleTtlSeconds + 1) * 1000);
    assertThat(cache.size()).isZero();
}
```

### New Tests for Version Checking

```java
@Test
void shouldNotEvictUpdatedEntry() throws InterruptedException {
    // Put initial value
    cache.put("key", "valueA");
    
    // Wait 1 second
    Thread.sleep(1000);
    
    // Update with new value (creates new version)
    cache.put("key", "valueB");
    
    // Wait for old ExpiryNode to fire
    Thread.sleep((staleTtlSeconds + 1) * 1000);
    
    // New value should still be present
    assertThat(cache.getStale("key")).contains("valueB");
}
```

---

## Optional Enhancement: O(log n) LRU Eviction

### Problem with Current LRU

```java
private void evictLeastRecentlyAccessed() {
    store.entrySet().stream()
        .min(...) // O(n) scan to find minimum
        .ifPresent(e -> store.remove(e.getKey()));
}
```

**Cost:** O(n) every time cache reaches max size.

### Solution: ConcurrentSkipListMap Index

```java
public class InMemoryCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> store;
    private final DelayQueue<ExpiryNode<K>> expiryQueue;
    
    // NEW: LRU index sorted by lastAccessedAt
    private final ConcurrentSkipListMap<Long, K> lruIndex = new ConcurrentSkipListMap<>();
    
    public void put(K key, V value) {
        synchronized (evictionLock) {
            // Remove old LRU entry if updating
            CacheEntry<V> oldEntry = store.get(key);
            if (oldEntry != null) {
                lruIndex.remove(oldEntry.getLastAccessedAt().toEpochMilli());
            }
            
            if (store.size() >= maxSize && !store.containsKey(key)) {
                evictLeastRecentlyAccessedFast();
            }
            
            CacheEntry<V> entry = new CacheEntry<>(value, ttlSeconds);
            store.put(key, entry);
            
            // Add to LRU index
            lruIndex.put(entry.getLastAccessedAt().toEpochMilli(), key);
            
            // Register in DelayQueue
            Instant expiryTime = entry.getCreatedAt().plusSeconds(staleTtlSeconds);
            expiryQueue.offer(new ExpiryNode<>(key, expiryTime, entry.getVersion()));
        }
    }
    
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        
        synchronized (evictionLock) {
            // Update LRU index
            lruIndex.remove(entry.getLastAccessedAt().toEpochMilli());
            entry.updateLastAccess();
            lruIndex.put(entry.getLastAccessedAt().toEpochMilli(), key);
        }
        
        hitCount.incrementAndGet();
        return Optional.of(entry.getValue());
    }
    
    private void evictLeastRecentlyAccessedFast() {
        // O(log n) - get first entry from sorted map
        Map.Entry<Long, K> oldest = lruIndex.firstEntry();
        if (oldest != null) {
            K key = oldest.getValue();
            store.remove(key);
            lruIndex.remove(oldest.getKey());
            evictionCount.incrementAndGet();
            log.debug("LRU eviction (fast): key={}", key);
        }
    }
}
```

**Benefits:**
- **O(log n) LRU eviction** vs O(n) scan
- **O(log n) per get()** for index update
- **Better scalability** for high-throughput caches

**Tradeoffs:**
- Additional memory: ~32 bytes per entry for index
- Slightly higher get() cost due to index maintenance
- More complex synchronization

**When to Use:**
- Cache size > 10,000 entries
- High put/get throughput (>1000 ops/sec)
- Frequent max-size evictions

---

## Migration Checklist

- [x] Create `ExpiryNode` class implementing `Delayed`
- [x] Add `getVersion()` method to `CacheEntry`
- [x] Replace `ScheduledExecutorService` with `DelayQueue`
- [x] Implement `runEvictionLoop()` with version checking
- [x] Add `@PostConstruct` for thread startup
- [x] Add `@PreDestroy` for graceful shutdown
- [x] Update `put()` to register `ExpiryNode` in `DelayQueue`
- [x] Remove `evictStaleEntries()` periodic scan method
- [x] Add logging for eviction events
- [x] Preserve all existing API contracts
- [x] Run existing unit tests
- [x] Add new tests for version checking
- [ ] Performance test with large cache (10K+ entries)
- [ ] Monitor CPU usage before/after in production
- [ ] Consider O(log n) LRU enhancement if needed

---

## Performance Monitoring

### Metrics to Track

```java
@RestController
@RequestMapping("/cache")
public class CacheController {
    
    @Autowired
    private InMemoryCache<String, Object> cache;
    
    @GetMapping("/stats")
    public CacheStats getStats() {
        return cache.getStats();
    }
}
```

**Response:**
```json
{
  "size": 8432,
  "hits": 125634,
  "misses": 3421,
  "staleHits": 234,
  "evictions": 1523,
  "ttlSeconds": 300,
  "staleTtlSeconds": 3600,
  "hitRate": 0.973
}
```

### JVM Monitoring

```bash
# Monitor eviction thread CPU usage
jstack <pid> | grep cache-delayqueue-eviction

# Monitor DelayQueue size
jcmd <pid> GC.heap_info | grep DelayQueue
```

---

## Conclusion

The DelayQueue-based implementation provides:

✅ **Better CPU efficiency** - Event-driven vs periodic scans  
✅ **Immediate eviction** - Entries removed exactly at stale TTL  
✅ **Concurrency safety** - Version checking prevents race conditions  
✅ **Spring integration** - Proper lifecycle management with @PostConstruct/@PreDestroy  
✅ **Production-ready** - Comprehensive logging and error handling  
✅ **Backward compatible** - All existing functionality preserved  

**Next Steps:**
1. Deploy to staging environment
2. Monitor CPU and memory metrics
3. Run load tests (10K+ entries, high throughput)
4. Consider O(log n) LRU enhancement if needed
5. Gradually roll out to production

---

**Author:** Senior Java Performance Engineer  
**Date:** 2026-05-30  
**Version:** 1.0
