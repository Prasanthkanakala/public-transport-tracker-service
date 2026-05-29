# Design Patterns – Public Transport Tracker

## Patterns Applied

### 1. Strategy Pattern
**Location:** `TransitApiClient` interface → `MtaApiClient`, `TransitLandApiClient`

Allows the service layer to switch between API providers at runtime without changing business logic. `TransportService.selectClient()` picks the best available implementation.

```
TransitApiClient (interface)
    ├── MtaApiClient          ← Strategy A: NYC MTA
    └── TransitLandApiClient  ← Strategy B: Transit.land (fallback)
```

### 2. Chain of Responsibility Pattern
**Location:** `TransportService.getTransportData()` degradation chain

Each handler in the chain either handles the request or passes it to the next:

```
OFFLINE CHECK → CACHE CHECK → LIVE API → STALE CACHE → MOCK DATA
```

If one stage fails, the next stage picks up, ensuring the request is always fulfilled.

### 3. Facade Pattern
**Location:** `CacheService` wrapping `InMemoryCache`

`CacheService` provides a clean, Spring-managed API hiding the complexity of key building, stat tracking, and raw `InMemoryCache` operations.

### 4. Builder Pattern
**Location:** All model classes via Lombok `@Builder`

```java
VehicleLocation.builder()
    .vehicleId("VEH-001")
    .latitude(40.758)
    .delaySeconds(960)
    .build();
```

### 5. HATEOAS / HAL Pattern
**Location:** `TransportResponse._links`

Every response includes hypermedia links to related resources, making the API self-discoverable.

### 6. Template Method (via Spring DI)
**Location:** `AppConfig.java` with `@Qualifier`

Named bean injection pattern provides swappable client implementations without subclassing.

---

## Why Option A (Resilience & Offline Mode)?

| Option | Why not chosen |
|--------|---------------|
| **Option B – Observability** | Spring Actuator already provides structured metrics (/actuator/health, /actuator/metrics). The key differentiator is resilience, not additional observability tooling. |
| **Option C – Data Reasoning** | ETA computation is included (RoutePlannerService). However, the more critical production concern for a transit app is ensuring availability when upstream APIs fail. Option A directly solves this. |
| **Option A – Resilience ✓** | Transit APIs have historically poor availability (rate limits, scheduled downtime, geopolitical changes). Option A ensures users always see useful data. The stale-cache mechanism is particularly valuable during planned maintenance windows. |