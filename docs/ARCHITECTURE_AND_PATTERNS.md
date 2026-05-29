# Architecture Deep Dive & Design Patterns

**Document Purpose:** Explain the architectural decisions and design patterns used throughout the codebase.  
**Audience:** Developers, architects, code reviewers

---

## Table of Contents

1. [Overall Architecture](#overall-architecture)
2. [Design Patterns Used](#design-patterns)
3. [Resilience Strategy](#resilience-strategy)
4. [Data Flow Patterns](#data-flow-patterns)
5. [Layered Architecture](#layered-architecture)
6. [API Design Principles](#api-design-principles)
7. [Frontend Architecture](#frontend-architecture)
8. [Testing Strategy](#testing-strategy)

---

## Overall Architecture

### High-Level System Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                         User's Browser                              │
│                       (React Frontend)                              │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 │ HTTP/AJAX
                                 │
                 ┌───────────────▼────────────────┐
                 │   nginx Reverse Proxy          │
                 │   (Port 80)                    │
                 └───────────────┬────────────────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
        ┌───────────▼──────┐     │      ┌────▼──────────┐
        │  Static Assets   │     │      │   API Calls   │
        │  (React bundle)  │     │      │  (Port 8080)  │
        └──────────────────┘     │      └────┬──────────┘
                                 │           │
                    ┌────────────┘           │
                    │                        │
              ┌─────▼────────────────────────▼────────────┐
              │   Spring Boot Backend (Port 8080)         │
              │                                            │
              │  ┌─────────────────────────────────────┐  │
              │  │      REST Controllers               │  │
              │  │  - TransportController              │  │
              │  │  - CacheController                  │  │
              │  └────────────────┬────────────────────┘  │
              │                   │                        │
              │  ┌────────────────▼────────────────────┐  │
              │  │      Service Layer                  │  │
              │  │  - TransportService                 │  │
              │  │  - AlertService                     │  │
              │  │  - RoutePlannerService              │  │
              │  │  - MockDataService                  │  │
              │  └────────────────┬────────────────────┘  │
              │                   │                        │
              │  ┌────────────────▼────────────────────┐  │
              │  │      Cache Layer                    │  │
              │  │  - InMemoryCache<K,V>               │  │
              │  │  - CacheService                     │  │
              │  └──────────────────────────────────────┘  │
              │                                            │
              │  ┌─────────────────────────────────────┐  │
              │  │      API Client Layer               │  │
              │  │  - MtaApiClient                     │  │
              │  │  - TransitLandApiClient             │  │
              │  │  - SeptaApiClient                   │  │
              │  └────────────────┬────────────────────┘  │
              │                   │                        │
              └───────────────────┼────────────────────────┘
                                  │
                  ┌───────────────┼────────────────┐
                  │               │                │
              ┌───▼──────┐  ┌────▼──────┐  ┌─────▼──────┐  ┌────▼─────┐
              │  MTA API │  │TransitLand │  │ SEPTA API  │  │  TfL API  │
              │ (NYC)    │  │ (Fallback) │  │(Philly)    │  │ (London)  │
              └──────────┘  └───────────┘  └────────────┘  └───────────┘
```

### Key Principles

1. **Layered Architecture** — Separation of concerns (controllers → services → data)
2. **Stateless Design** — Each request is independent, no session state
3. **Dependency Injection** — Spring wires dependencies automatically
4. **Contract-based APIs** — Interfaces define contracts, multiple implementations
5. **Resilience First** — Never completely fail, always provide some response

---

## Design Patterns Used

### 1. Chain of Responsibility (5-Stage Degradation)

**Pattern Definition:** A sequence of handlers, each deciding whether to process or pass to the next.

**Implementation in TransportService:**

```
Stage 1: Is offline mode ON?
  ├─ YES → Return MOCK data (stop here)
  └─ NO → Check Stage 2

Stage 2: Is fresh cached data available (< TTL)?
  ├─ YES → Return CACHE data (stop here)
  └─ NO → Check Stage 3

Stage 3: Try to fetch from live APIs
  ├─ SUCCESS → Cache and return LIVE data (stop here)
  └─ FAILURE → Check Stage 4

Stage 4: Is stale cached data available (< stale-TTL)?
  ├─ YES → Return STALE_CACHE data with warning (stop here)
  └─ NO → Check Stage 5

Stage 5: Return MOCK (synthetic) data as final fallback
```

**Why This Pattern?**
- Each handler is independent and testable
- Graceful degradation without any failure points
- Frontend always gets a response
- Data freshness is communicated via metadata

---

### 2. Strategy Pattern (Multiple API Clients)

**Pattern Definition:** Multiple implementations of the same interface, selected at runtime.

**Implementation:**

```java
// Contract
public interface TransitApiClient {
    List<VehicleLocation> fetchVehicleLocations(String city, String routeId);
    // ... other methods
}

// Implementations
class MtaApiClient implements TransitApiClient { ... }
class TransitLandApiClient implements TransitApiClient { ... }
class SeptaApiClient implements TransitApiClient { ... }

// Selection logic
private TransitApiClient selectClient(String city) {
    return city.equalsIgnoreCase("nyc") ? mtaClient :
           city.equalsIgnoreCase("philly") ? septaClient :
           transitLandClient;
}
```

**Why This Pattern?**
- New API providers can be added without changing `TransportService`
- Easy to test each API client in isolation
- Follows Open/Closed Principle: open for extension, closed for modification

---

### 3. Adapter Pattern (Standardizing External APIs)

**Pattern Definition:** Convert external data formats into internal formats.

**Implementation:**

MTA API returns:
```json
{
  "Siri": {
    "ServiceDelivery": {
      "VehicleMonitoringDelivery": [
        {
          "VehicleActivity": [
            { "MonitoredVehicleJourney": { "VehicleLocation": { "Latitude": 40.7, "Longitude": -74.0 }, ... } }
          ]
        }
      ]
    }
  }
}
```

We convert to:
```json
{
  "vehicleId": "VEH-001",
  "latitude": 40.7,
  "longitude": -74.0,
  ...
}
```

**Parser Code:**
```java
private List<VehicleLocation> parseMtaVehicles(String jsonResponse) {
    // Navigate nested SIRI structure
    // Extract coordinates, calculate delays
    // Map to VehicleLocation model
    // Return standardized list
}
```

**Why This Pattern?**
- Isolates the application from API changes
- Standardizes data across different providers
- Makes testing easier (mock the parser)

---

### 4. Observer Pattern (Auto-Refresh)

**Frontend Implementation:**

```javascript
useEffect(() => {
  const interval = setInterval(fetch, 30000);  // Observe every 30s
  return () => clearInterval(interval);        // Cleanup
}, [dependencies]);
```

**Why This Pattern?**
- Components reactively update when data changes
- Automatic refresh without manual intervention
- Proper cleanup prevents memory leaks

---

### 5. Repository Pattern (Cache as Data Store)

**Pattern Definition:** Abstract data access through a repository interface.

**Implementation:**

```java
public class CacheService {
    public Optional<TransportData> get(String key) { ... }           // Read
    public void put(String key, TransportData value) { ... }         // Write
    public Optional<TransportData> getStale(String key) { ... }      // Fallback read
    public void delete(String key) { ... }                           // Delete
    public void clear() { ... }                                      // Clear all
}
```

**Why This Pattern?**
- Single point of access for cached data
- Easy to swap cache implementation (add Redis later)
- Encapsulates cache logic

---

### 6. Template Method Pattern (Error Handling)

**Pattern Definition:** Define algorithm skeleton, let subclasses fill in details.

**Implementation:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Template: catch all exceptions
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(...) { /* Implementation */ }
    
    @ExceptionHandler(TransitApiException.class)
    public ResponseEntity<?> handleApiError(...) { /* Implementation */ }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(...) { /* Implementation */ }
}
```

All handlers follow the template:
1. Catch specific exception type
2. Log the error
3. Return error response with appropriate HTTP status
4. Never expose internal details

---

## Resilience Strategy

### The "Never Fails" Guarantee

Our system implements **defensive programming** with graceful degradation:

```
┌─────────────────────────────────┐
│   User Request                  │
└────────────┬────────────────────┘
             │
    ┌────────▼─────────┐
    │ Try Offline Mode │───► If ON → Return MOCK (0ms response)
    └────────┬─────────┘
             │ Continue if OFF
             │
    ┌────────▼────────────┐
    │ Try Fresh Cache     │───► If HIT → Return CACHE (< 50ms)
    │ (< 300 seconds)     │
    └────────┬────────────┘
             │ No hit
             │
    ┌────────▼───────────────────┐
    │ Try Live API Call           │
    │ - MTA (5s timeout)          │
    │ - TransitLand (5s timeout)  │───► If SUCCESS → Cache + return LIVE (< 6s)
    └────────┬───────────────────┘
             │ All failed
             │
    ┌────────▼──────────────────┐
    │ Try Stale Cache           │
    │ (< 3600 seconds)          │───► If HIT → Return STALE_CACHE + warning
    └────────┬──────────────────┘
             │ Empty
             │
    ┌────────▼──────────────┐
    │ Return MOCK (Fallback)│───► Always succeeds
    └───────────────────────┘
```

### Why This Works

1. **Fast Path** — Cache hit (< 50ms) for most requests
2. **Graceful Degradation** — Each stage is better than nothing
3. **User Transparency** — `dataSource` field tells user data freshness
4. **No External Dependency** — Mock data is generated/loaded locally
5. **Automatic Recovery** — When APIs return online, we use fresh data again

---

## Data Flow Patterns

### 1. Request/Response Cycle

```
Frontend Component
    ↓
useTransport Hook (calls apiService)
    ↓
apiService.getTransportData() (HTTP GET /api/v1/transport)
    ↓
TransportController (receive request)
    ↓
TransportService.getTransportData() (apply degradation chain)
    ├─ TransportData data
    ├─ Cache operations
    ├─ API client calls
    └─ AlertService enrichment
    ↓
TransportResponse (wrap with metadata + links)
    ↓
HTTP Response (200 OK + JSON)
    ↓
Frontend processes metadata (data source, age)
    ↓
React re-renders with new data
    ↓
User sees updated UI
```

### 2. Cache Lifecycle

```
PUT operation:
  1. Check if cache is full (> max size)
  2. If full, evict least-recently-accessed entry
  3. Store (data, timestamp, TTL)
  4. Return cached entry

GET operation (fresh):
  1. Look up entry
  2. Check: Is entry expired (age > TTL)?
  3. If NOT expired → Return entry (HIT, with age)
  4. If expired → Try stale window

GET operation (stale):
  1. Check: Is entry within stale-TTL (age < stale-TTL)?
  2. If YES → Return entry (STALE_HIT, with warning flag)
  3. If NO → Return empty (cache miss)

EVICTION:
  1. Background thread runs every 60 seconds
  2. Scan all entries
  3. Remove entries outside stale-TTL window
  4. Log eviction count
```

### 3. Error Propagation

```
External API Error
    ↓
HttpClient throws exception (timeout, connection refused, 5xx response)
    ↓
MtaApiClient.fetchVehicleLocations() catches
    ↓
Throws TransitApiException
    ↓
TransportService catches (in try/catch)
    ↓
Tries next provider OR falls back to cache/mock
    ↓
GlobalExceptionHandler catches (if uncaught)
    ↓
Returns error response (400 Bad Request, 503 Service Unavailable, etc.)
    ↓
Frontend receives error
    ↓
UI displays user-friendly message
```

---

## Layered Architecture

### Layer 1: Presentation Layer (Frontend)

**Responsibility:** User interface and interaction

**Components:**
- React components (App, RouteSearch, VehicleMap, etc.)
- CSS styling
- State management (hooks)

**Principles:**
- Responsive design
- Accessible UI
- Client-side rendering
- Declarative (describe what to show)

### Layer 2: API Layer (Controllers)

**Responsibility:** HTTP endpoint exposure

**Components:**
- `TransportController` (8 endpoints)
- `CacheController` (2 endpoints)

**Principles:**
- RESTful URL structure (`/api/v1/resource`)
- HTTP method semantics (GET, DELETE, etc.)
- Status codes (200, 400, 503, etc.)
- Request/response validation

### Layer 3: Service Layer (Business Logic)

**Responsibility:** Core application logic

**Components:**
- `TransportService` (degradation chain, enrichment)
- `AlertService` (rule evaluation)
- `RoutePlannerService` (journey calculation)
- `MockDataService` (synthetic data)
- `CacheService` (cache management)

**Principles:**
- No direct HTTP knowledge
- Testable in isolation
- Reusable from multiple controllers
- Transaction boundaries

### Layer 4: Data Access Layer

**Sub-layers:**
- **Cache Layer** (`CacheService`, `InMemoryCache`) — Local fast storage
- **API Client Layer** (`MtaApiClient`, `TransitLandApiClient`, `SeptaApiClient`, `TflApiClient`) — External data

**Principles:**
- Repository pattern for cache
- Strategy pattern for clients
- Adapter pattern for formatting

### Layer 5: Infrastructure Layer

**Responsibility:** Non-business concerns

**Components:**
- Exception handlers
- Security filters
- CORS configuration
- Health checks
- Logging and monitoring

**Principles:**
- Transparent to business logic
- Configurable behavior
- Security by default

---

## API Design Principles

### 1. REST Conventions

✅ **Resource-Based URLs** (nouns, not verbs)
```
/api/v1/transport          ← Resource
/api/v1/transport/vehicles ← Sub-resource
```

❌ **NOT action-based**
```
/api/v1/getTransport       ← DON'T DO THIS
/api/v1/fetchVehicles      ← DON'T DO THIS
```

✅ **HTTP Methods Match Intent**
```
GET    /api/v1/transport           → Retrieve data
DELETE /api/v1/cache               → Delete cache
```

### 2. Consistent Response Format

All responses follow the same envelope:
```json
{
  "data": { /* actual response */ },
  "metadata": { /* source, age, etc. */ },
  "_links": { /* HATEOAS navigation */ }
}
```

**Benefits:**
- Frontend code is simpler (one response parser)
- Discoverability (links tell clients what's available)
- Transparency (metadata explains data freshness)

### 3. Error Responses

Consistent error format:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Descriptive message",
  "path": "/api/v1/endpoint",
  "timestamp": "ISO 8601"
}
```

### 4. Versioning

URLs include API version (`/api/v1/...`):
- **V1** — Current stable version
- **V2** — Future incompatible changes without breaking v1

### 5. Parameters

Query parameters for optional filtering:
```
GET /api/v1/transport?city=nyc&routeId=A&offline=true
```

Required parameters cause validation errors:
```
GET /api/v1/transport/arrivals?stopId=A27N  ← Required
```

---

## Frontend Architecture

### Component Structure

```
App (root)
├── RouteSearch      (city + route selection)
├── AlertBanner      (warning notifications)
├── ArrivalBoard     (departure times table)
├── VehicleMap       (schematic positions)
├── CrowdingIndicator (capacity visualization)
├── RoutePlanner     (journey planning)
└── OfflineToggle    (mode switcher)
```

Each component:
- Has a `.jsx` file (logic)
- Has a `.css` file (styling)
- Is stateless (gets props from parent/hooks)
- Is testable in isolation

### State Management Strategy

**Global State** (in `App.jsx`):
- `city`, `routeId` — User selections
- `offline` — Mode toggle
- `activeTab` — Tab navigation

**Component State** (in custom hooks):
- `transportData`, `metadata` — From API
- `loading`, `error` — Request status

**Why this approach?**
- Minimal state (easy to reason about)
- Unidirectional data flow
- No complex state library needed

### Data Fetching Pattern

```javascript
// In useTransport hook
useEffect(() => {
  // Fetch when dependencies change
  const fetch = async () => {
    try {
      const response = await getTransportData({ city, routeId, offline });
      setData(response.data);
      setMetadata(response.metadata);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Initial fetch
  fetch();

  // Auto-refresh every 30s
  const interval = setInterval(fetch, 30000);

  // Cleanup
  return () => clearInterval(interval);
}, [city, routeId, offline]); // Dependencies
```

**Benefits:**
- Data fetched automatically when inputs change
- Auto-refresh without manual intervention
- Proper cleanup prevents memory leaks
- Error handling built-in

---

## Testing Strategy

### Unit Tests

**What to test:** Individual functions/methods in isolation

**Example:**
```java
@Test
void testCacheEviction_whenFullRemovesLRU() {
    InMemoryCache<String, String> cache = new InMemoryCache<>(maxSize: 3);
    cache.put("A", "value A");
    cache.put("B", "value B");
    cache.put("C", "value C");
    
    // Access A (makes it recently-accessed)
    cache.get("A");
    
    // Add D (cache full, should evict B or C, not A)
    cache.put("D", "value D");
    
    assertTrue(cache.get("A").isPresent());
    assertFalse(cache.get("B").isPresent());
}
```

### Service Tests

**What to test:** Business logic with mocked dependencies

**Example:**
```java
@Test
void testTransportService_Stage3LiveApiFailure_FallsbackToStaleCache() {
    // Mock API to throw exception
    when(mtaClient.isAvailable()).thenReturn(false);
    when(transitLandClient.isAvailable()).thenReturn(false);
    
    // Add stale data to cache
    cache.put("nyc:a", staleData);
    
    // Request should return stale data
    TransportResponse response = service.getTransportData("nyc", "A", false);
    assertEquals("STALE_CACHE", response.getMetadata().getDataSource());
}
```

### Integration Tests

**What to test:** Multiple layers working together

**Example:**
```java
@Test
void testEndToEnd_VehicleRequest_ReturnsValidResponse() {
    // Make HTTP request to actual endpoint
    ResponseEntity<TransportResponse> response = 
        restTemplate.getForEntity("/api/v1/transport?city=nyc", TransportResponse.class);
    
    // Verify response structure
    assertEquals(200, response.getStatusCodeValue());
    assertNotNull(response.getBody().getData().getVehicles());
    assertNotNull(response.getBody().getMetadata());
}
```

### Test Coverage Goals

- **Unit tests:** 90%+ of service layer logic
- **Integration tests:** All controller endpoints
- **Mutation tests:** Critical algorithms (cache, degradation)

---

## Conclusion

The architecture achieves:

✅ **Resilience** — Never completely fails
✅ **Scalability** — Stateless, cache-friendly
✅ **Maintainability** — Clear layering, design patterns
✅ **Testability** — Dependencies mockable, isolated concerns
✅ **User Experience** — Always-available, transparent data freshness
