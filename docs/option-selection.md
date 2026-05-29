# Implementation Option Selection – Public Transport Tracker

## Decision: Option A – Resilience & Offline Mode ✅

---

## All Three Options Evaluated

---

### Option A – Resilience & Offline Mode

**What it requires:**
- In-memory cache with TTL
- Serve stale data on upstream failure
- Explicit degradation strategy

**Assessment:**

Transit APIs (`MTA`, `Transit.land`) are third-party services with documented reliability issues:
- NYC MTA API has scheduled maintenance windows (typically overnight)
- Rate limiting kicks in during peak commuter hours
- Transit.land can have feed ingestion delays of 5–30 minutes
- Network partitions and latency spikes affect real-time GTFS feeds

Without a resilience strategy, a single upstream failure means the end user sees a broken app
at the exact moment they need transit information the most — during disruptions.

**Verdict: CHOSEN.** Directly addresses the highest-probability failure mode in production.

---

### Option B – Observability & Ops

**What it requires:**
- Structured logging
- Metrics (latency, failure rate)
- Health / readiness endpoints
- Explain SLOs

**Assessment:**

Spring Boot Actuator already provides `/actuator/health`, `/actuator/metrics`, and `/actuator/info`
out of the box. Adding Micrometer metrics and structured JSON logging over Logback would take
roughly the same effort as Option A but yields lower marginal value because:

- Observability without resilience means you watch the service fail, not prevent it.
- The team can add a metrics stack (Prometheus + Grafana) on top of Option A at any time.
- Health endpoints are already implemented as part of the baseline (Docker `HEALTHCHECK` + Spring Actuator).

**Verdict: NOT chosen as primary.** Core observability is included anyway (structured logging,
SLO documentation, health endpoints). Option B is a layer on top of a resilient service, not
a substitute for one.

---

### Option C – Data Reasoning

**What it requires:**
- ETA calculation and route planning algorithm
- Explain trust boundaries of transit data
- Handle missing / delayed vehicle updates

**Assessment:**

ETA calculation and route planning are functionally useful but they depend entirely on data
quality. If the upstream API returns stale or missing vehicle positions:

- An ETA algorithm built on bad data produces confidently wrong results.
- Users acting on incorrect ETAs is worse than users seeing a "data unavailable" notice.
- Route planning (`RoutePlannerService`) is still implemented to meet the functional requirements,
  but without a resilience foundation it cannot be trusted.

**Verdict: NOT chosen as primary.** `RoutePlannerService` and ETA confidence scoring are
implemented as functional features. Trust boundaries and missing-update handling are documented
below. Option C's value is maximised only once Option A guarantees data availability.

---

## Why Option A Wins

```
Risk Ranking for a Transit Tracker in Production
─────────────────────────────────────────────────
#1  Upstream API goes down during morning rush    ← Option A solves this
#2  Stale data served without user knowing        ← Option A solves this
#3  No visibility when things go wrong            ← Option B (partially covered)
#4  Wrong ETAs due to missing vehicle data        ← Option C (partially covered)
```

A transit tracking app that is down when services are disrupted is useless.
Option A guarantees users always receive useful information, even during the worst conditions.

---

## What Was Implemented for Option A

### 1. In-Memory Cache with TTL

**Class:** `InMemoryCache<K, V>` (`cache/InMemoryCache.java`)

Built from scratch using Java standard library only — no Ehcache, Caffeine, or Redis.

```
InMemoryCache
├── store: ConcurrentHashMap<K, CacheEntry<V>>   ← thread-safe storage
├── CacheEntry<V>
│    ├── value          – the cached object
│    ├── createdAt      – Instant of insertion
│    ├── ttlSeconds     – how long the entry is "fresh"
│    └── lastAccessedAt – used for approximate LRU eviction
│
├── get(key)            → Optional<V>   fresh only (within TTL)
├── getStale(key)       → Optional<V>   expired but within stale window
├── put(key, val)       → evicts LRU if at maxSize
└── Background eviction (ScheduledExecutorService daemon thread)
      └── runs every ttlSeconds, purges entries exceeding staleTtlSeconds
```

**Tunable parameters (environment variables):**

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `CACHE_TTL_SECONDS` | 300 (5 min) | How long fresh data is served from cache |
| `CACHE_STALE_TTL_SECONDS` | 3600 (1 hr) | Maximum age before stale data is discarded |
| `CACHE_MAX_SIZE` | 1000 entries | Prevents unbounded memory growth |

**Cache key format:** `"{city}:{routeId}"` — e.g., `"nyc:A"`, `"london:central"`

**Stats tracked (used for observability):**
- Hit count, miss count, stale-hit count, eviction count
- Hit rate = `hits / (hits + misses)`
- Exposed at `GET /api/v1/cache/stats`

---

### 2. Serve Stale Data on Upstream Failure

**Class:** `TransportService` (`service/TransportService.java`)

The `getStale(key)` method on `CacheService` is called exclusively when a live API fetch
throws a `TransitApiException`. It returns data that has exceeded its TTL but is still within
the stale window (default: 1 hour). The response metadata transparently communicates this:

```json
{
  "metadata": {
    "dataSource": "STALE_CACHE",
    "cached": true,
    "cacheAgeSeconds": 450,
    "offlineMode": false
  }
}
```

The React frontend detects `dataSource === "STALE_CACHE"` and renders:

> ⚠ Live API unavailable – showing cached data (450s old)

This banner informs users their data may not be current **without hiding the data from them**.

---

### 3. Explicit Degradation Strategy

The degradation chain is implemented in `TransportService.getTransportData()` with five
deterministic stages, each with a clear exit condition:

```
Stage 1 ──── offline param == true
             └──▶ return MockData immediately (no API call attempted)
                  metadata.dataSource = "MOCK"
                  metadata.offlineMode = true

Stage 2 ──── CacheService.get(key) returns Optional.of(data)   [within TTL]
             └──▶ return CachedData
                  metadata.dataSource = "CACHE"
                  metadata.cacheAgeSeconds = age in seconds

Stage 3 ──── TransitApiClient.isAvailable() == true
             └──▶ fetch from MTA or TransitLand
                  └──▶ success: cache result, return LiveData
                       metadata.dataSource = "LIVE"
                       metadata.cacheAgeSeconds = 0

Stage 4 ──── Stage 3 threw TransitApiException
             CacheService.getStale(key) returns Optional.of(staleData)   [within staleTTL]
             └──▶ return StaleData
                  metadata.dataSource = "STALE_CACHE"
                  metadata.cacheAgeSeconds = actual age

Stage 5 ──── No stale data available
             └──▶ return MockData
                  metadata.dataSource = "MOCK"
```

**Invariant:** The method NEVER throws an exception to the controller. The user always gets data.

**Client selection within Stage 3:**

```
selectClient():
  1. MtaApiClient.isAvailable()        → true  use NYC MTA (primary)
  2. TransitLandApiClient.isAvailable() → true  use Transit.land (fallback)
  3. Both false                         → throw TransitApiException → triggers Stage 4
```

Each `isAvailable()` check uses a 2-second connect timeout to avoid blocking the request
thread for longer than necessary.

---

### 4. Offline Mode Toggle

Two mechanisms are provided:

**Global offline mode** (server-side, via environment variable):
```
OFFLINE_MODE=true   # all requests served from mock data, no API calls ever made
```
Useful for demo environments, development without API keys, or pre-production testing.

**Per-request offline mode** (client-controlled, via query parameter):
```
GET /api/v1/transport?city=nyc&routeId=A&offline=true
```
The React frontend exposes this as a toggle switch (📵 Offline / 📡 Live) in the header.
When activated, all API calls are bypassed for that client session.

---

### 5. Mock Data Provider

**Class:** `MockDataService` (`service/MockDataService.java`)

Serves as the last-resort fallback. Loads from JSON files in `resources/mock-data/`:

| File | Contents |
|------|---------|
| `vehicles.json` | 5 vehicles on route A with varied delay/occupancy states |
| `arrivals.json` | 5 upcoming arrivals with a 16-minute delay to trigger the threshold alert |
| `alerts.json` | DISRUPTION (A/C suspension) + WEATHER alert — both trigger conditional alerts |

If JSON files are missing (e.g., stripped during packaging), the service generates synthetic
data programmatically so the fallback is never empty.

**Mock data is designed to trigger all four conditional alerts** so the full UI is exercisable
without real API credentials:

| Alert triggered by mock data | Condition |
|-------------------------------|-----------|
| DELAY | Vehicle `VEH-A-001` has `delaySeconds=960` (16 min > 15 min threshold) |
| DISRUPTION | Alert `type=DISRUPTION`, `effect=SUSPENSION` |
| CROWDING | `CrowdingInfo{level=FULL, occupancyPercentage=95}` on `VEH-A-001` |
| WEATHER | Alert `cause=WEATHER` on bus route alert |

---

## Option B Elements Included Anyway

Even though Option B was not the primary choice, the following observability features are present:

| Feature | Where |
|---------|-------|
| Structured logging (SLF4J/Logback) | Every class uses `@Slf4j`; key events logged at INFO/WARN with structured context |
| Health endpoint | `GET /actuator/health` → UP/DOWN with component details |
| Readiness endpoint | `GET /actuator/health` — same endpoint, used by Docker `HEALTHCHECK` |
| Info endpoint | `GET /actuator/info` — app name, version, description |
| Metrics metadata | `GET /actuator/metrics` — JVM + HTTP metrics via Micrometer |
| Cache metrics | `GET /api/v1/cache/stats` — hit rate, miss count, stale hits, evictions |
| SLO documentation | See table below |

**SLOs (Service Level Objectives):**

| SLO | Target | How Achieved |
|-----|--------|-------------|
| Availability | 99.5% | Cache + stale fallback ensure partial availability even when all APIs are down |
| P99 response time (LIVE) | < 2000 ms | HTTP client timeout set to 5s; availability check timeout 2s |
| P99 response time (CACHE) | < 50 ms | ConcurrentHashMap read + Java serialization only |
| Data freshness | ≤ 5 min (live), ≤ 60 min (stale) | Controlled by `CACHE_TTL_SECONDS` and `CACHE_STALE_TTL_SECONDS` |
| Cache hit rate | > 70% under normal load | Eviction only on TTL expiry or maxSize; auto-refresh on every cache miss |
| Error visibility | 100% logged | `GlobalExceptionHandler` logs all exceptions with path + timestamp |

---

## Option C Elements Included Anyway

| Feature | Where |
|---------|-------|
| ETA calculation | `ArrivalPrediction.minutesToArrival`, `delaySeconds` — derived from `predictedArrival - scheduledArrival` |
| Route planning algorithm | `RoutePlannerService` — builds direct and alternative plans with legs, confidence scoring |
| Confidence scoring | `calculateConfidence()` — degrades for non-realtime arrivals and high-severity alerts |
| Trust boundaries | Documented below |
| Missing/delayed vehicle handling | `safeCall()` wrapper in `TransportService` — any individual data fetch failure returns empty list, not exception |

**Trust Boundaries of Transit Data:**

| Data Type | Trust Level | Rationale |
|-----------|-------------|-----------|
| GTFS-RT VehiclePosition (real-time) | HIGH if age < 90s | Vehicles report every 30–60s; beyond 90s the position is interpolated |
| GTFS-RT StopTimeUpdate (predicted arrivals) | MEDIUM | Predictions degrade rapidly when a vehicle is stopped or signal is lost |
| GTFS Schedule (static) | HIGH for structure, LOW for times | Static schedules are authority for stop sequences; planned times drift with traffic |
| MTA Alerts API | HIGH | Editorial content; accurate but may lag actual conditions by 5–15 min |
| Transit.land aggregated feeds | MEDIUM | Aggregator introduces additional latency; feed quality varies by operator |

**Handling Missing / Delayed Vehicle Updates:**

```
If a vehicle has not reported for > 5 minutes:
  → VehicleLocation.status remains last known value
  → delaySeconds is not recalculated (stale value flagged in metadata)
  → RoutePlannerService.calculateConfidence() applies CONFIDENCE_PENALTY_PER_MISSING
    for each non-realtime arrival prediction
  → Frontend renders last known position in grey rather than route colour (future enhancement)

If all vehicles on a route are missing:
  → safeCall() returns empty List<VehicleLocation>
  → No crash; data.vehicles = [] in response
  → Frontend shows: "No vehicles currently tracked on route A"
```

---

## Summary

| | Option A | Option B | Option C |
|--|---------|---------|---------|
| **Chosen as primary** | ✅ Yes | ❌ No | ❌ No |
| **Core implementation** | Full | Baseline only | Partial |
| **Reason for choice** | Highest-impact failure mode for transit apps | Already covered by Spring Actuator | Depends on Option A being in place first |
| **Elements included anyway** | — | Logging, health, metrics, SLOs | ETAs, route planning, confidence, trust docs |
