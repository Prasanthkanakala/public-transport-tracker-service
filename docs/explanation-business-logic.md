# Business Logic Explanation — Written for Everyone

> This document explains the core brain of the application — how it decides what data to show,  
> when to use cached data, how alerts work, and how route planning is done.  
> No technical background needed.

---

## Table of Contents

1. [The Big Picture — What Problem Are We Solving?](#1-the-big-picture)
2. [Why We Chose "Option A: Resilience & Offline Mode"](#2-why-option-a)
3. [The 5-Stage Degradation Chain](#3-the-5-stage-degradation-chain)
4. [TransportService — The Brain](#4-transportservice--the-brain)
5. [AlertService — The Warning System](#5-alertservice--the-warning-system)
6. [RoutePlannerService — The Navigator](#6-routeplannerservice--the-navigator)
7. [MockDataService — The Safety Net](#7-mockdataservice--the-safety-net)
8. [How Every Endpoint Works End-to-End](#8-how-every-endpoint-works-end-to-end)
9. [Design Patterns Used and Why](#9-design-patterns-used-and-why)

---

## 1. The Big Picture

Imagine you're standing at a subway station. You want to know:
- **Where is my train right now?** (vehicle locations)
- **When will it arrive?** (arrival predictions)
- **Are there any problems?** (service alerts)
- **How crowded will it be?** (crowding info)
- **What's the best way to get from A to B?** (route planning)

This application answers all those questions by:
1. Asking real transit systems (like NYC's MTA) for live data
2. Processing that data (calculating delays, detecting problems)
3. Presenting it in a clean, real-time dashboard

The **critical challenge**: transit APIs go down. They're slow. They return errors. Users should NEVER see an error page. They should always see *something* useful.

---

## 2. Why Option A: Resilience & Offline Mode

We were given three options:

| Option | Focus | Core Challenge |
|---|---|---|
| **A: Resilience** | App works even when APIs fail | Cache management, graceful degradation |
| **B: Performance** | Fast responses at scale | Load balancing, connection pooling |
| **C: Multi-city** | Support multiple cities | Dynamic configuration, provider routing |

**We chose Option A because:**

1. **User experience is #1** — A transit app that shows "Error: service unavailable" is useless. Commuters are in a hurry. Even slightly outdated data is better than no data.

2. **Real-world realism** — Transit APIs genuinely go down frequently. MTA's API has documented outages. Building resilience isn't theoretical — it's necessary.

3. **Demonstrates depth** — Option A requires the most sophisticated internal architecture: custom caching with two TTL tiers, mock data generation, client failover, and a 5-stage fallback chain. It shows engineering depth.

4. **Subsumes the others** — Our resilience implementation naturally includes multi-provider support (Option C's concern) and caching for performance (Option B's concern).

---

## 3. The 5-Stage Degradation Chain

This is the most important concept in the entire application. It's a safety chain:

```
User makes a request
        │
        ▼
┌─ Stage 1: Is offline mode ON? ─── YES → Return MOCK data immediately
│       │ NO
│       ▼
├─ Stage 2: Is there FRESH cached data? ─── YES → Return CACHE data
│       │ NO
│       ▼
├─ Stage 3: Can we fetch LIVE data from APIs? ─── YES → Return LIVE data (and store in cache)
│       │ FAILED
│       ▼
├─ Stage 4: Is there STALE (expired but recent) cached data? ─── YES → Return STALE_CACHE data
│       │ NO
│       ▼
└─ Stage 5: Return MOCK (synthetic) data
```

**Real-world analogy:**

Imagine you're a news reporter at a press conference:

1. **Offline mode** = "I've been told to use prepared statements only" → use prepared script
2. **Fresh cache** = "I asked this question 2 minutes ago and wrote down the answer" → read your notes
3. **Live API** = "I'll ask the spokesperson directly right now" → get fresh answer
4. **Stale cache** = "The spokesperson is unavailable, but I asked 30 minutes ago" → use your older notes (with a caveat)
5. **Mock data** = "Nobody is available and I have no notes" → use the press release they handed out earlier

**The key insight:** The user ALWAYS gets data. The `metadata.dataSource` field tells them HOW fresh it is, and the UI shows a warning banner if it's stale or mock.

---

## 4. TransportService — The Brain

This is the central orchestration service. It's the decision-maker that implements the 5-stage chain.

### Main method: `getTransportData(city, routeId, offline)`

```java
public TransportResponse<TransportData> getTransportData(String city, String routeId, Boolean offline) {
    boolean isOffline = (offline != null) ? offline : globalOfflineMode;
    String cacheKey = cacheService.buildKey(city, routeId);  // e.g., "nyc:a"
```

**Line by line:**
- `offline != null ? offline : globalOfflineMode` — if the user explicitly passed `?offline=true`, use that. Otherwise, use the global setting from `application.properties`. This lets individual requests override the server-wide setting.
- `buildKey("nyc", "A")` → `"nyc:a"` — a unique identifier for this city+route combination. All NYC route A data is stored under this key.

**Stage 1: Offline shortcut**
```java
if (isOffline) {
    TransportData mockData = mockDataService.getMockData(city, routeId);
    enrichData(mockData);
    return buildResponse(mockData, "MOCK", null, city, routeId, true);
}
```
If offline mode is on, don't even TRY to call APIs. Immediately return mock data. This is useful when:
- User has no internet connection
- Transit API is in planned maintenance
- Developer is testing the UI without API keys

**Stage 2: Fresh cache**
```java
Optional<TransportData> cached = cacheService.get(cacheKey);
if (cached.isPresent()) {
    Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
    return buildResponse(cached.get(), "CACHE", age, city, routeId, false);
}
```
`Optional` is Java's way of saying "this might have a value, or it might be empty." Instead of checking `if (cached != null)`, we use `if (cached.isPresent())`. This is safer because it forces the programmer to explicitly handle the "no data" case.

If data was cached less than 5 minutes ago (the TTL), return it immediately. This is a performance optimization — instead of calling MTA's API for every request, we reuse recent data. The `cacheAgeSeconds` field tells the frontend "this data is 47 seconds old."

**Stage 3: Live API fetch**
```java
try {
    TransportData liveData = fetchFromApis(city, routeId);
    enrichData(liveData);
    cacheService.put(cacheKey, liveData);
    return buildResponse(liveData, "LIVE", 0L, city, routeId, false);
} catch (TransitApiException ex) {
```
If no fresh cache exists, call the actual transit APIs. `fetchFromApis()` internally:
1. Picks the best available provider (MTA first, TransitLand as fallback)
2. Fetches vehicles, alerts, and crowding data in parallel
3. Returns everything in a `TransportData` object

`enrichData()` runs the AlertService to generate conditional alerts (more on this below).

Once fetched, the data is stored in the cache for future requests.

**Stage 4: Stale cache fallback**
```java
Optional<TransportData> stale = cacheService.getStale(cacheKey);
if (stale.isPresent()) {
    return buildResponse(stale.get(), "STALE_CACHE", staleAge, city, routeId, false);
}
```
If the live API call FAILED (threw a `TransitApiException`), we try the stale cache. This is data that's past its 5-minute freshness window but still within the 1-hour stale window. It's not ideal, but it's better than nothing. The frontend shows a warning: "Showing cached data (324s old)."

**Stage 5: Mock fallback**
```java
TransportData mock = mockDataService.getMockData(city, routeId);
enrichData(mock);
return buildResponse(mock, "MOCK", null, city, routeId, false);
```
If even stale cache is empty (first-ever request, or cache was cleared), return mock data. The frontend shows: "Live API unavailable — showing sample data."

### Provider Selection: `selectClient()`

```java
private TransitApiClient selectClient() {
    if (mtaClient.isAvailable()) return mtaClient;
    if (transitLandClient.isAvailable()) return transitLandClient;
    throw new TransitApiException("All transit API providers are unavailable");
}
```

This tries MTA first. If MTA's health check fails (returns 500 or times out), it tries TransitLand. If both fail, it throws an exception — which triggers Stage 4 (stale cache).

### Safe calls: `safeCall()`

```java
private <T> List<T> safeCall(Supplier<List<T>> supplier, String label) {
    try {
        return supplier.get();
    } catch (Exception e) {
        log.warn("Failed to fetch {}: {}", label, e.getMessage());
        return List.of();  // return empty list, don't crash
    }
}
```

When fetching live data, vehicles and alerts are fetched separately. If the vehicle call succeeds but the alerts call fails, we don't want to throw away the vehicle data. `safeCall()` catches individual failures and returns an empty list instead of crashing the entire request.

---

## 5. AlertService — The Warning System

The AlertService is like a fire alarm system. It examines all the data and generates human-readable warnings.

### The 4 Rules

**Rule 1: Significant Delays**
```java
private void checkVehicleDelays(List<VehicleLocation> vehicles, List<AlertMessage> out) {
    int thresholdSeconds = significantDelayMinutes * 60;  // 15 * 60 = 900 seconds

    boolean significantDelay = vehicles.stream()
            .anyMatch(v -> v.getDelaySeconds() != null && v.getDelaySeconds() > thresholdSeconds);

    if (significantDelay) {
        out.add(AlertMessage.builder()
                .type("DELAY").level("WARNING")
                .message("Significant delays - Plan accordingly")
                .build());
    }
}
```

**Plain English:** "Look at all vehicles. If ANY vehicle is more than 15 minutes late, show a delay warning."

**Why 15 minutes?** Small delays (1-5 minutes) are normal in transit. Only significant delays (15+ minutes) are worth alerting about. The threshold is configurable in `application.properties`.

**What is `.stream().anyMatch()`?** Think of it as a conveyor belt. `.stream()` puts all vehicles on the belt. `.anyMatch(condition)` checks each one: "does ANY vehicle match this condition?" It stops as soon as it finds one — efficient.

**Rule 2: Service Disruptions**
```java
boolean hasDisruption = serviceAlerts.stream()
        .anyMatch(a -> "DISRUPTION".equalsIgnoreCase(a.getType())
                || "SUSPENSION".equalsIgnoreCase(a.getEffect())
                || "DETOUR".equalsIgnoreCase(a.getEffect()));
```
"If any alert is a DISRUPTION, or if any service is SUSPENDED or DETOURED, show a disruption warning."

**Why `equalsIgnoreCase`?** Different providers may send "disruption", "DISRUPTION", or "Disruption". Case-insensitive comparison handles all variations.

**Rule 3: Crowding**
```java
boolean highCrowding = crowding.stream()
        .anyMatch(c -> "HIGH".equalsIgnoreCase(c.getLevel())
                || "FULL".equalsIgnoreCase(c.getLevel())
                || "CRUSHED_STANDING_ROOM_ONLY".equalsIgnoreCase(c.getGtfsOccupancyStatus()));
```
"If any vehicle is HIGH or FULL capacity, warn users to consider the next service."

`CRUSHED_STANDING_ROOM_ONLY` is an actual standard from the GTFS (General Transit Feed Specification) — the global standard for transit data.

**Rule 4: Weather**
```java
boolean weatherImpact = serviceAlerts.stream()
        .anyMatch(a -> "WEATHER".equalsIgnoreCase(a.getCause())
                || "WEATHER".equalsIgnoreCase(a.getType()));
```
"If any alert is caused by weather, warn users about potential schedule impacts."

### Output

Each rule produces an `AlertMessage` with:
- `type` — DELAY, DISRUPTION, CROWDING, or WEATHER
- `level` — WARNING (yellow), ERROR (red), or INFO (blue)
- `message` — Human-readable text shown in the UI banner
- `icon` — Icon name for the frontend to display

These alerts are added to `TransportData.conditionalAlerts` and rendered as banners at the top of the UI.

---

## 6. RoutePlannerService — The Navigator

This service creates journey plans from point A to point B.

### How It Works

```java
public List<RoutePlan> plan(String from, String to, String city,
                            List<ServiceAlert> activeAlerts,
                            List<ArrivalPrediction> arrivals) {
    List<RoutePlan> plans = new ArrayList<>();

    // Always create a direct route
    plans.add(buildDirectPlan(from, to, city, activeAlerts, arrivals, 0));

    // If there are disruptions, offer an alternative with a transfer
    if (!activeAlerts.isEmpty()) {
        plans.add(buildAlternativePlan(from, to, city, activeAlerts, arrivals));
    }

    return plans;
}
```

**Logic:**
1. Always create Plan A — the direct route (no transfers)
2. If there are active alerts (disruptions, delays), ALSO create Plan B — an alternative route with a transfer point

**Why only create Plan B when alerts exist?** In normal conditions, the direct route is best. Adding alternatives clutters the UI. But when there are disruptions, alternatives become essential — "your usual route is disrupted, here's a workaround."

### Duration Estimation

```java
private int estimateDuration(String from, String to) {
    int hash = Math.abs((from + to).hashCode());
    return 10 + (hash % 36);  // Returns 10 to 45 minutes
}
```

In production, this would use a real graph search algorithm on the transit network. For this case study, we use a deterministic hash: given the same origin and destination, it always returns the same duration. This ensures consistent behavior in tests and demos.

**Why `hashCode()`?** It generates a number from the text of the origin and destination. "Times Square" + "Atlantic Ave" always produces the same number, so the estimated duration is always the same for the same pair. But different pairs get different durations (10-45 minutes) — realistic variety.

### Confidence Score

```java
private double calculateConfidence(List<ArrivalPrediction> arrivals, List<ServiceAlert> alerts) {
    double confidence = 1.0;  // Start at 100%

    // Penalty for each arrival prediction that isn't real-time
    long nonRealtime = arrivals.stream().filter(a -> !Boolean.TRUE.equals(a.getRealtime())).count();
    confidence -= nonRealtime * CONFIDENCE_PENALTY_PER_MISSING;  // -0.15 per missing

    // Penalty for high-severity alerts
    long highAlerts = alerts.stream()
            .filter(a -> "HIGH".equalsIgnoreCase(a.getSeverity())).count();
    confidence -= highAlerts * 0.2;  // -0.20 per high-severity alert

    return Math.max(confidence, 0.1);  // Never below 10%
}
```

**Plain English:** Start with 100% confidence. For each arrival prediction that ISN'T based on real-time GPS data, subtract 15%. For each HIGH severity alert, subtract 20%. Never go below 10%.

This gives users a sense of trust: "85% confident" vs "30% confident — expect delays."

### Disruption Detection

```java
private boolean isRouteDisrupted(String from, String to, List<ServiceAlert> alerts) {
    return alerts.stream().anyMatch(a ->
            "DISRUPTION".equalsIgnoreCase(a.getType())
            || "SUSPENSION".equalsIgnoreCase(a.getEffect())
    );
}
```

If any disruption affects the network, mark the plan as "DISRUPTED" — the UI shows this in red text.

---

## 7. MockDataService — The Safety Net

This service generates fake-but-realistic data for when APIs are unavailable.

### Data Sources (in priority order)

1. **JSON files** (`src/main/resources/mock-data/vehicles.json`, `arrivals.json`, `alerts.json`) — hand-crafted realistic data
2. **Synthetic generators** — if JSON files are missing or can't be read, generate data in code

### Why Two Layers?

The JSON files might not exist (e.g., if someone deletes them or is running in a stripped-down Docker image). The synthetic generators ensure we ALWAYS have fallback data — the application truly never fails.

### Synthetic Data Details

```java
for (int i = 1; i <= 5; i++) {
    int delaySeconds = (i % 3 == 0) ? 960 : (i % 2 == 0) ? 120 : 0;
```

This creates 5 vehicles:
- Vehicle 1: on time (0 delay)
- Vehicle 2: 2 minutes late (120 seconds)
- Vehicle 3: **16 minutes late** (960 seconds) — deliberately exceeds the 15-minute alert threshold
- Vehicle 4: 2 minutes late
- Vehicle 5: on time

**Why include a 16-minute delay?** To trigger the AlertService's delay rule. This means even in mock/offline mode, users see the conditional alert banners — the full feature set is demonstrated.

**Why `new Random(42L)`?** The `42L` is a seed value. A seeded random number generator produces the SAME "random" numbers every time. This makes tests predictable and reproducible — you always get the same mock data.

---

## 8. How Every Endpoint Works End-to-End

### Example: User visits the dashboard

1. **Browser** sends `GET /api/v1/transport?city=nyc&routeId=A`
2. **TransportController** receives the request, calls `transportService.getTransportData("nyc", "A", null)`
3. **TransportService** runs the 5-stage chain:
   - Checks offline mode (no)
   - Checks cache for `"nyc:a"` (miss)
   - Calls `selectClient()` → MTA is available → calls MTA API
   - MTA returns vehicle positions, alerts
   - `enrichData()` runs AlertService → detects 16-minute delay → adds DELAY alert
   - Stores in cache under `"nyc:a"`
   - Returns response with `dataSource: "LIVE"`
4. **TransportController** wraps in `ResponseEntity.ok()` → HTTP 200
5. **Browser** receives JSON with vehicles, arrivals, alerts, conditional alerts

### Example: MTA goes down mid-day

1. First request at 10:00 — LIVE data fetched and cached
2. Request at 10:03 — CACHE hit (data is 3 minutes old, within 5-minute TTL)
3. Request at 10:06 — cache expired, try LIVE API → **MTA is down**
4. Stage 4: stale cache exists (10 minutes old, within 1-hour stale window) → return STALE_CACHE
5. Frontend shows: "Live API unavailable — showing cached data (360s old)"

### Example: First-ever request with all APIs down

1. No cache exists yet
2. MTA is down, TransitLand is down
3. No stale cache available
4. MockDataService returns synthetic data
5. Frontend shows: "Live API unavailable — showing sample data"

---

## 9. Design Patterns Used and Why

### 1. Strategy Pattern (API Clients)

**What:** Define a common interface, multiple implementations can be swapped.  
**Where:** `TransitApiClient` interface → `MtaApiClient`, `TransitLandApiClient`.  
**Why:** Adding a new city's transit API = create one new class. Zero changes to existing code.  
**Analogy:** Like having multiple delivery services (FedEx, UPS, DHL) that all accept the same shipping label.

### 2. Chain of Responsibility (Degradation Chain)

**What:** A series of handlers, each gets a chance to handle the request. If one can't, pass to the next.  
**Where:** The 5-stage chain in `TransportService.getTransportData()`.  
**Why:** Clean separation — each stage has one job. Adding a 6th stage (e.g., "fetch from partner service") is trivial.  
**Analogy:** Like a phone tree — "Press 1 for sales, press 2 for support." Each option handles what it can.

### 3. Facade Pattern (CacheService)

**What:** A simplified interface over a complex subsystem.  
**Where:** `CacheService` wraps `InMemoryCache`.  
**Why:** The rest of the application calls `cacheService.get(key)` without knowing about `ConcurrentHashMap`, `AtomicInteger`, `ScheduledExecutorService`, or thread safety.  
**Analogy:** A car dashboard — you see speed and fuel. You don't see pistons and injectors.

### 4. Builder Pattern (All Models)

**What:** Construct complex objects step by step.  
**Where:** `VehicleLocation.builder().vehicleId("X").latitude(40.7).build()`.  
**Why:** Instead of `new VehicleLocation("X", null, null, 40.7, -74.0, null, ...)` with 15 arguments in the right order, the builder lets you set only what you need.  
**Analogy:** Ordering a custom sandwich — "I want bread, then cheese, then lettuce, done" vs "give me sandwich #47."

### 5. Template Method (Endpoint Handlers)

**What:** Different endpoints follow the same structure: check offline → check cache → try live → try stale → use mock.  
**Where:** `getTransportData()`, `getArrivals()`, `getServiceAlerts()` all follow the same pattern.  
**Why:** Consistency. Every endpoint degrades the same way. Users get the same reliability guarantees everywhere.

### 6. Null Object Pattern (Empty Lists)

**What:** Return empty collections instead of `null`.  
**Where:** `safeCall()` returns `List.of()` on failure; API methods return `List.of()` for invalid inputs.  
**Why:** Code that consumes the data never needs `if (list != null)` checks. It can always iterate safely.  
**Analogy:** Instead of saying "I have no answer" (null), say "I have an empty answer" — the recipient can process it the same way.

---

## Summary of Decisions

| Decision | Reasoning |
|---|---|
| Custom cache (not Redis/Caffeine) | Two-tier TTL (fresh + stale) is central to our design; off-the-shelf caches don't support this natively |
| Java HttpClient (not OkHttp) | Zero extra dependencies; built into Java 11+ |
| 5-minute TTL, 1-hour stale | Transit data changes every few minutes, but hour-old data is still directionally useful |
| 15-minute delay threshold | Anything shorter is normal; 15+ minutes impacts commuter decisions |
| Provider failover (MTA → TransitLand) | Redundancy; single points of failure are unacceptable for a transit app |
| Seeded Random for mock data | Reproducible tests; same mock data every time |
| HATEOAS response envelope | Frontend discovers URLs dynamically; API is self-describing |
| Stateless security | No sessions = no session hijacking; scales horizontally |
| `@RestControllerAdvice` error handling | Centralized; every endpoint returns consistent error format |