# Redis Migration Plan - Dual-Key Strategy

## Executive Summary

This document outlines the migration strategy from in-memory cache to Redis for the Public Transport Tracker system, using a **dual-key approach** to support both fresh TTL (5 minutes) and stale TTL (60 minutes) semantics.

---

## Current Architecture

### In-Memory Cache Structure
```java
ConcurrentHashMap<String, CacheEntry<TransportData>>

class CacheEntry {
    TransportData value;
    Instant createdAt;
    long ttlSeconds;        // 300s (5 min)
    Instant lastAccessedAt;
}
```

### Key Characteristics
- **Fresh TTL**: 5 minutes (300 seconds)
- **Stale TTL**: 60 minutes (3600 seconds)
- **Max Size**: 1000 entries
- **Eviction**: LRU + TTL-based background cleanup
- **Thread Safety**: ConcurrentHashMap + computeIfAbsent()

---

## Redis Migration Strategy

### Option 1: Dual-Key Strategy (RECOMMENDED)

#### Key Design
```
Fresh Key: "transit:fresh:{city}:{routeId}"
Stale Key: "transit:stale:{city}:{routeId}"
```

#### Data Structure
```json
{
  "arrivals": [...],
  "vehicles": [...],
  "metadata": {
    "city": "nyc",
    "routeId": "1",
    "fetchedAt": "2024-01-15T10:30:00Z",
    "source": "MTA_API"
  }
}
```

#### Implementation

```java
@Service
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, TransportData> redisTemplate;
    
    @Value("${transit.cache.ttl-seconds:300}")
    private long freshTtl;
    
    @Value("${transit.cache.stale-ttl-seconds:3600}")
    private long staleTtl;

    // ─── Write Operation ─────────────────────────────────────────────────────────────

    public void put(String city, String routeId, TransportData data) {
        String freshKey = buildFreshKey(city, routeId);
        String staleKey = buildStaleKey(city, routeId);
        
        // Write to both keys atomically using Redis pipeline
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                // Fresh key with 5-minute TTL
                operations.opsForValue().set(freshKey, data, Duration.ofSeconds(freshTtl));
                
                // Stale key with 60-minute TTL
                operations.opsForValue().set(staleKey, data, Duration.ofSeconds(staleTtl));
                
                return null;
            }
        });
        
        log.debug("Cached to Redis: fresh={}, stale={}", freshKey, staleKey);
    }

    // ─── Read Operations ───────────────────────────────────────────────────────────

    /**
     * Returns fresh data (within 5-minute TTL)
     */
    public Optional<TransportData> get(String city, String routeId) {
        String freshKey = buildFreshKey(city, routeId);
        TransportData data = redisTemplate.opsForValue().get(freshKey);
        
        if (data != null) {
            log.debug("Redis HIT (fresh): key={}", freshKey);
            return Optional.of(data);
        }
        
        log.debug("Redis MISS (fresh): key={}", freshKey);
        return Optional.empty();
    }

    /**
     * Returns stale data (expired fresh, but within 60-minute window)
     * Used as fallback when live API fails
     */
    public Optional<TransportData> getStale(String city, String routeId) {
        String staleKey = buildStaleKey(city, routeId);
        TransportData data = redisTemplate.opsForValue().get(staleKey);
        
        if (data != null) {
            log.warn("Serving STALE data from Redis: key={}", staleKey);
            return Optional.of(data);
        }
        
        log.debug("Redis MISS (stale): key={}", staleKey);
        return Optional.empty();
    }

    /**
     * CORRECTED: Prevents cache stampede using Redis SETNX + TTL
     */
    public TransportData getOrCompute(String city, String routeId, 
                                       Supplier<TransportData> valueLoader) {
        // Try fresh cache first
        Optional<TransportData> fresh = get(city, routeId);
        if (fresh.isPresent()) {
            return fresh.get();
        }
        
        // Use distributed lock to prevent stampede
        String lockKey = "lock:" + buildFreshKey(city, routeId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(10));
        
        if (Boolean.TRUE.equals(acquired)) {
            try {
                // Double-check cache after acquiring lock
                Optional<TransportData> recheck = get(city, routeId);
                if (recheck.isPresent()) {
                    return recheck.get();
                }
                
                // Fetch from API
                log.info("Cache MISS - fetching from live API: city={}, routeId={}", city, routeId);
                TransportData data = valueLoader.get();
                put(city, routeId, data);
                return data;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // Another thread is fetching - wait and retry
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getOrCompute(city, routeId, valueLoader);
        }
    }

    // ─── Key Building ───────────────────────────────────────────────────────────────────

    private String buildFreshKey(String city, String routeId) {
        return String.format("transit:fresh:%s:%s", 
                normalizeKey(city), normalizeKey(routeId));
    }

    private String buildStaleKey(String city, String routeId) {
        return String.format("transit:stale:%s:%s", 
                normalizeKey(city), normalizeKey(routeId));
    }

    private String normalizeKey(String value) {
        return (value != null && !value.isBlank()) 
                ? value.toLowerCase().strip() 
                : "all";
    }
}
```

---

## Migration Phases

### Phase 1: Dual-Write Mode (Week 1-2)
```java
@Service
public class HybridCacheService {
    private final InMemoryCache inMemoryCache;
    private final RedisCacheService redisCache;
    
    public void put(String key, TransportData data) {
        inMemoryCache.put(key, data);  // Primary
        redisCache.put(key, data);      // Secondary (write-through)
    }
    
    public Optional<TransportData> get(String key) {
        // Read from in-memory first
        return inMemoryCache.get(key);
    }
}
```

### Phase 2: Dual-Read Mode (Week 3-4)
```java
public Optional<TransportData> get(String key) {
    Optional<TransportData> redis = redisCache.get(key);
    if (redis.isPresent()) {
        return redis;
    }
    
    // Fallback to in-memory
    Optional<TransportData> memory = inMemoryCache.get(key);
    if (memory.isPresent()) {
        // Backfill Redis
        redisCache.put(key, memory.get());
    }
    return memory;
}
```

### Phase 3: Redis-Only Mode (Week 5+)
```java
public Optional<TransportData> get(String key) {
    return redisCache.get(key);
}
```

---

## Configuration

### application.yml
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    ssl: true
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 1000ms

transit:
  cache:
    ttl-seconds: 300        # 5 minutes fresh
    stale-ttl-seconds: 3600 # 60 minutes stale
    max-size: 10000         # Redis can handle more
```

### Redis Configuration Bean
```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, TransportData> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TransportData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use JSON serialization
        Jackson2JsonRedisSerializer<TransportData> serializer = 
                new Jackson2JsonRedisSerializer<>(TransportData.class);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
```

---

## Monitoring & Metrics

### Key Metrics to Track
```java
@Component
public class CacheMetrics {
    
    @Scheduled(fixedDelay = 60000)
    public void logRedisStats() {
        RedisConnection connection = redisTemplate.getConnectionFactory()
                .getConnection();
        
        Properties info = connection.info("stats");
        
        log.info("Redis Cache Stats: " +
                "keyspace_hits={}, " +
                "keyspace_misses={}, " +
                "evicted_keys={}, " +
                "used_memory_human={}",
                info.getProperty("keyspace_hits"),
                info.getProperty("keyspace_misses"),
                info.getProperty("evicted_keys"),
                info.getProperty("used_memory_human"));
    }
}
```

---

## Rollback Plan

If Redis fails in production:

1. **Immediate Fallback**: Switch `@Primary` bean back to `InMemoryCache`
2. **Health Check**: Monitor Redis connectivity
3. **Circuit Breaker**: Auto-fallback on Redis errors

```java
@Service
public class ResilientCacheService {
    
    @CircuitBreaker(name = "redis", fallbackMethod = "fallbackToMemory")
    public Optional<TransportData> get(String key) {
        return redisCache.get(key);
    }
    
    public Optional<TransportData> fallbackToMemory(String key, Exception e) {
        log.error("Redis failure - falling back to in-memory cache", e);
        return inMemoryCache.get(key);
    }
}
```

---

## Cost & Performance Comparison

| Metric | In-Memory | Redis (Managed) |
|--------|-----------|----------------|
| **Latency** | <1ms | 1-3ms |
| **Capacity** | 1000 entries | 10,000+ entries |
| **Persistence** | Lost on restart | Persistent |
| **Multi-instance** | No sharing | Shared cache |
| **Cost** | Free (RAM) | $50-200/month |

---

## Conclusion

The **dual-key strategy** provides:
- ✅ Clean separation of fresh vs. stale data
- ✅ Native Redis TTL management
- ✅ No metadata overhead
- ✅ Simple migration path
- ✅ Cache stampede protection via distributed locks

**Recommended Timeline**: 5 weeks from dual-write to Redis-only mode.