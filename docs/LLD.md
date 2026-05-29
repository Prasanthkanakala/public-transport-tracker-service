# Low-Level Design – Public Transport Tracker

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Network                           │
│                                                                 │
│  ┌──────────────────────┐      ┌────────────────────────────┐  │
│  │  React Frontend       │─────▶│  Spring Boot Backend        │  │
│  │  nginx :80            │      │  :8080                      │  │
│  │                       │      │                             │  │
│  │  RouteSearch          │      │  TransportController        │  │
│  │  VehicleMap (SVG)     │ HTTP │  CacheController            │  │
│  │  ArrivalBoard         │ REST │                             │  │
│  │  AlertBanner          │      │  TransportService           │  │
│  │  CrowdingIndicator    │      │  AlertService               │  │
│  │  RoutePlanner         │      │  RoutePlannerService        │  │
│  │  OfflineToggle        │      │  MockDataService            │  │
│  └──────────────────────┘      │                             │  │
│                                 │  InMemoryCache<K,V>         │  │
│                                 │  CacheService               │  │
│                                 │                             │  │
│                                 │  MtaApiClient    ──────────▶│  External
│                                 │  TransitLandApiClient ─────▶│  APIs
│                                 └────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Package Structure

```
com.transport.tracker
├── TransportTrackerApplication.java   ← Spring Boot entry point
│
├── config/
│   ├── AppConfig.java         ← Bean wiring, ObjectMapper
│   ├── SecurityConfig.java    ← Spring Security (stateless)
│   ├── WebConfig.java         ← CORS config
│   └── OpenApiConfig.java     ← Swagger/OpenAPI 3.0
│
├── controller/
│   ├── TransportController.java ← REST endpoints /api/v1/transport/*
│   └── CacheController.java     ← Cache stats/management endpoints
│
├── service/
│   ├── TransportService.java      ← Core orchestration + degradation strategy
│   ├── AlertService.java          ← Conditional alert rule evaluation
│   ├── RoutePlannerService.java   ← Route planning algorithm
│   └── MockDataService.java       ← Fallback mock data provider
│
├── client/
│   ├── TransitApiClient.java      ← Interface (ISP principle)
│   ├── MtaApiClient.java          ← NYC MTA implementation
│   └── TransitLandApiClient.java  ← Transit.land implementation
│
├── cache/
│   ├── CacheEntry.java            ← Value wrapper with TTL metadata
│   ├── InMemoryCache.java         ← Pure-Java thread-safe cache
│   └── CacheService.java          ← Spring-managed cache facade
│
├── model/
│   ├── VehicleLocation.java       ← GPS position + delay
│   ├── ArrivalPrediction.java     ← Stop arrival with ETA
│   ├── ServiceAlert.java          ← Disruption/delay notification
│   ├── RoutePlan.java             ← Journey plan with legs
│   ├── CrowdingInfo.java          ← Occupancy data
│   ├── TransportData.java         ← Aggregate response data
│   ├── AlertMessage.java          ← Conditional alert message
│   └── TransportResponse.java     ← HATEOAS HAL wrapper
│
└── exception/
    ├── TransitApiException.java       ← Upstream API failure
    ├── ErrorResponse.java             ← Standard error body
    └── GlobalExceptionHandler.java    ← @RestControllerAdvice
```

## 3. Data Flow – Option A (Resilience & Offline Mode)

```
Client Request
    │
    ▼
TransportController.getTransportData(city, routeId, offline)
    │
    ▼
TransportService.getTransportData()
    │
    ├─[offline == true]──────────────────────────────────▶ MockDataService
    │                                                           │
    ├─[cache HIT]──────────────────────────────────────▶ return CachedData
    │                                                           │
    ├─[cache MISS]─▶ selectClient()                            │
    │                    │                                      │
    │                    ├─[MTA available]──▶ MtaApiClient      │
    │                    └─[MTA down]──────▶ TransitLandClient  │
    │                                            │              │
    │                    ┌───────────────────────┘              │
    │                    │                                      │
    │               [API success]──▶ cache.put() ──▶ return LiveData
    │                    │
    │               [API failure]
    │                    │
    ├─[stale cache]──────┘──▶ return StaleData (with warning)
    │
    └─[no stale]─────────────▶ MockDataService ──▶ return MockData
```

## 4. Cache Design

```
InMemoryCache<K, V>
  │
  ├── store: ConcurrentHashMap<K, CacheEntry<V>>
  │    └── CacheEntry<V>
  │         ├── value: V
  │         ├── createdAt: Instant
  │         ├── ttlSeconds: long         (default: 300s = 5 min)
  │         ├── lastAccessedAt: Instant
  │         └── isExpired()             → createdAt + ttl < now
  │              isStaleFor(stale)      → createdAt + stale < now
  │
  ├── get(key)      → Optional<V>  (fresh only, within TTL)
  ├── getStale(key) → Optional<V>  (expired but within staleTTL=3600s)
  ├── put(key, val) → evicts LRU if at maxSize
  │
  └── Background eviction (ScheduledExecutorService)
       └── Every ttlSeconds: removes entries exceeding staleTTL
```

| Parameter | Default | Env Var |
|-----------|---------|---------|
| TTL | 300s (5 min) | CACHE_TTL_SECONDS |
| Stale TTL | 3600s (1 hr) | CACHE_STALE_TTL_SECONDS |
| Max size | 1000 entries | CACHE_MAX_SIZE |

## 5. Alert Evaluation Rules

| Condition | Threshold | Message | Level |
|-----------|-----------|---------|-------|
| Vehicle delay | > 15 minutes | "Significant delays - Plan accordingly" | WARNING |
| Service alert type | DISRUPTION or effect=SUSPENSION/DETOUR | "Service alert - Check alternative routes" | ERROR |
| Crowding level | HIGH or FULL | "Vehicle at capacity - Consider next service" | WARNING |
| Alert cause | WEATHER | "Weather impact on schedule" | INFO |

## 6. API Client Selection Strategy

```
selectClient():
  1. MtaApiClient.isAvailable()  → true?  use MTA
  2. TransitLandApiClient.isAvailable() → true?  use TransitLand
  3. Both unavailable → throw TransitApiException
     → triggers stale/mock fallback in TransportService
```

Availability check uses a 2-second HTTP timeout to minimise latency impact.

## 7. Security Design

- **Stateless**: No sessions, no CSRF
- **API key protection**: Keys injected via env vars (MTA_API_KEY optional, TRANSITLAND_API_KEY), never hardcoded
- **X-Content-Type-Options**: nosniff
- **X-Frame-Options**: DENY
- **CORS**: Explicit origin whitelist (no wildcard)
- **Input validation**: `@NotBlank` and `@Validated` at controller layer
- **Error messages**: Generic messages returned to client, full info only in server logs

## 8. Design Patterns Used

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | TransitApiClient interface | Swap MTA/TransitLand without changing service layer |
| **Chain of Responsibility** | TransportService degradation chain | LIVE → CACHE → STALE → MOCK |
| **Template Method** | AbstractApiClient (future) | Common HTTP request lifecycle |
| **Factory / Bean Wiring** | AppConfig @Qualifier | Named client injection |
| **Facade** | CacheService | Hides InMemoryCache complexity from service layer |
| **Builder** | All model classes (Lombok) | Immutable, readable object construction |
| **HATEOAS / HAL** | TransportResponse._links | Discoverable REST API |

## 9. SOLID Adherence

- **S** – Single Responsibility: Each class has one clear purpose
- **O** – Open/Closed: New API providers implement TransitApiClient without changing service logic
- **L** – Liskov: MtaApiClient and TransitLandApiClient are interchangeable
- **I** – Interface Segregation: TransitApiClient defines the minimum needed contract
- **D** – Dependency Inversion: Service depends on TransitApiClient interface, not concrete classes

## 10. 12-Factor App Compliance

| Factor | Implementation |
|--------|---------------|
| Config | All config via env vars; 3 profiles: dev/prod |
| Dependencies | Declared in build.gradle |
| Processes | Stateless Spring Boot; no in-process state shared between requests |
| Port Binding | Configured via server.port / PORT env var |
| Logs | Structured to stdout (Spring logging → console) |
| Disposability | Fast startup; graceful shutdown via Spring context |
| Dev/Prod Parity | Docker Compose for local dev mirrors production |

## 11. SLOs (Service Level Objectives)

| SLO | Target |
|-----|--------|
| Availability | 99.5% (cache ensures partial availability even when APIs down) |
| P99 API Response Time (LIVE) | < 2000ms |
| P99 API Response Time (CACHE) | < 50ms |
| Cache Hit Rate | > 70% under normal load |
| Data Freshness | ≤ 5 minutes for live queries; up to 1 hour for stale fallback |