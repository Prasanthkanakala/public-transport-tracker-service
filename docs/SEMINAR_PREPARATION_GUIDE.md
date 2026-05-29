# Project Mastery & Seminar Preparation Guide

**Goal:** Master the Public Transport Tracker codebase to present confidently and answer any question  
**Estimated Time:** 8-12 hours for complete mastery  
**Last Updated:** May 7, 2026

---

## Table of Contents

1. [Quick Overview (30 min)](#phase-1-quick-overview)
2. [Core Concepts (2 hours)](#phase-2-core-concepts)
3. [Deep Dive by Layer (4 hours)](#phase-3-deep-dive)
4. [Hands-On Practice (2 hours)](#phase-4-hands-on-practice)
5. [Presentation Preparation (1 hour)](#phase-5-presentation-preparation)
6. [Seminar Q&A Prep (1 hour)](#phase-6-qa-preparation)

---

## PHASE 1: Quick Overview (30 minutes)

### What You'll Learn
The big picture of what this system does and why it matters.

### Learning Steps

#### Step 1.1: Understand the Problem (10 min)
**Read:** [GETTING_STARTED.md](./GETTING_STARTED.md) - "Quick Start" section

**Key Questions to Answer:**
- What problem does this system solve?
- Who are the users?
- What information do they need?

**Expected Understanding:**
- Commuters need real-time transit info (location, ETA, alerts, capacity, route planning)
- Transit APIs are unreliable (go down, timeout, return errors)
- The system must NEVER fail completely

#### Step 1.2: See It In Action (10 min)
**Do This:**
```bash
# Start the system
docker-compose up --build

# In another terminal, try these
curl http://localhost:8080/api/v1/transport?city=nyc&routeId=A | jq
```

**Open in Browser:**
- Frontend: http://localhost (see the UI)
- API Docs: http://localhost:8080/swagger-ui/index.html (interactive API)

**What to Notice:**
- What data fields are returned?
- What does the metadata tell you?
- What are the different data sources (LIVE, CACHE, STALE_CACHE, MOCK)?

#### Step 1.3: Understand the Stack (10 min)
**Read:** [COMPLETE_PROJECT_DOCUMENTATION.md](./COMPLETE_PROJECT_DOCUMENTATION.md) - Section "Technology Stack"

**Key Points to Remember:**
- **Backend:** Java 17, Spring Boot, custom HTTP client (no heavy frameworks)
- **Frontend:** React 18, custom SVG map (no map library)
- **Cache:** Custom InMemoryCache (not Redis)
- **APIs:** MTA (NYC), TransitLand (fallback), SEPTA (Philly), TfL (London)

**Memorize This:**
```
Frontend (React)
    ↓ HTTP
Backend (Spring Boot)
    ├─ Cache (in-memory)
    ├─ Services (business logic)
    └─ API Clients (MTA, TransitLand, SEPTA, TfL)
```

---

## PHASE 2: Core Concepts (2 hours)

### What You'll Learn
The fundamental concepts that make this system unique and resilient.

### Learning Steps

#### Step 2.1: The 5-Stage Degradation Chain (30 min) ⭐ CRITICAL

**Read:** [explanation-business-logic.md](./explanation-business-logic.md) - Sections 2 & 3

**This is THE key concept. Understand deeply:**

```
Stage 1: Offline mode? → Return MOCK immediately
Stage 2: Fresh cache (<300s)? → Return CACHE (fast)
Stage 3: Live API working? → Return LIVE (fetch)
Stage 4: Stale cache (<3600s)? → Return STALE_CACHE (warning)
Stage 5: Fallback? → Return MOCK (safety net)
```

**Real-World Analogy:**
- Offline = "Kitchen closed, use pre-made plate"
- Fresh cache = "Just made this 2 min ago, plate is hot"
- Live API = "Calling chef now"
- Stale cache = "Made 30 min ago, still OK"
- Mock = "Picture of menu item"

**Memorize:**
- User ALWAYS gets data
- We just tell them how fresh it is via `metadata.dataSource`
- This ensures the service NEVER completely fails

**Practice Question:** *"What happens if MTA API is down and cache is empty?"*
Answer: System returns MOCK data with a warning.

#### Step 2.2: Design Patterns (30 min)

**Read:** [ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md) - Section "Design Patterns Used"

**Must Understand These 6 Patterns:**

1. **Chain of Responsibility** — The 5-stage degradation chain
   - Each stage decides: handle or pass to next
   - Real code: `TransportService.getTransportData()`

2. **Strategy Pattern** — Multiple API clients (MTA, TransitLand, SEPTA, TfL)
   - Interface: `TransitApiClient`
   - Implementations: `MtaApiClient`, `TransitLandApiClient`, `SeptaApiClient`, `TflApiClient`
   - Selection: `selectClient(city)` method

3. **Adapter Pattern** — Data format conversion
   - MTA returns nested SIRI JSON
   - We convert to standardized `VehicleLocation` model
   - Parser method: `parseMtaVehicles()`

4. **Observer Pattern** — Auto-refresh (frontend)
   - React hook auto-fetches every 30 seconds
   - When data changes, UI updates automatically

5. **Repository Pattern** — Cache access
   - `CacheService` provides: `get()`, `put()`, `getStale()`, `delete()`
   - Abstracts cache implementation (swap Redis later)

6. **Template Method** — Error handling
   - `GlobalExceptionHandler` defines template
   - Each exception type fills in details
   - Always returns consistent error format

**For Each Pattern, Know:**
- What problem it solves
- Where it's used in this codebase
- One code example

#### Step 2.3: REST API Design (30 min)

**Read:** [API.md](./API.md) - Sections 1-2

**Key Concepts:**

1. **Resource-Based URLs**
   ```
   ✅ /api/v1/transport (resource)
   ✅ /api/v1/transport/vehicles (sub-resource)
   ❌ /api/v1/getTransport (action-based - avoid)
   ```

2. **HTTP Methods Match Intent**
   - `GET /api/v1/transport` — Retrieve data
   - `DELETE /api/v1/cache` — Remove cache

3. **Consistent Response Envelope**
   ```json
   {
     "data": { /* actual response */ },
     "metadata": { /* dataSource, age, timestamp */ },
     "_links": { /* HATEOAS navigation */ }
   }
   ```

4. **HATEOAS (Hypermedia As The Engine Of Application State)**
   - Response includes links to related resources
   - Clients don't hardcode URLs
   - API is self-documenting

**Know All 8 Endpoints:**
```
1. GET /api/v1/transport — Everything
2. GET /api/v1/transport/vehicles — Vehicle positions
3. GET /api/v1/transport/arrivals — ETAs
4. GET /api/v1/transport/alerts — Service alerts
5. GET /api/v1/transport/plan — Route planning
6. GET /api/v1/transport/crowding — Capacity
7. GET /api/v1/cache/stats — Cache health
8. DELETE /api/v1/cache — Cache management
```

#### Step 2.4: Data Models (30 min)

**Read:** [API.md](./API.md) - Section "Data Models"

**Know These Models:**

| Model | Key Fields | Purpose |
|-------|-----------|---------|
| `VehicleLocation` | vehicleId, lat, lon, speed, delay, occupancy | Vehicle position |
| `ArrivalPrediction` | stopId, scheduledArrival, predictedArrival, delay | When vehicle arrives |
| `ServiceAlert` | type, severity, affectedRoutes, activeWindow | Disruptions/warnings |
| `CrowdingInfo` | vehicleId, occupancyLevel, percentFull | Vehicle capacity |
| `RoutePlan` | origin, destination, legs, transfers, duration | Journey suggestion |
| `AlertMessage` | type, level, message, icon | Processed alert for UI |
| `TransportResponse` | data, metadata, _links | API response wrapper |

**Memorize:** What data each endpoint returns (mix of these models)

---

## PHASE 3: Deep Dive by Layer (4 hours)

### What You'll Learn
How the system is organized into layers and how each layer works.

### Learning Steps

#### Step 3.1: Frontend Layer (1 hour)

**Read:** [explanation-frontend.md](./explanation-frontend.md)

**Architecture:**
```
App.jsx (main component)
├── RouteSearch (city/route selector)
├── AlertBanner (warnings)
├── ArrivalBoard (departure times)
├── VehicleMap (vehicle positions)
├── CrowdingIndicator (capacity bars)
├── RoutePlanner (journey planner)
└── OfflineToggle (mode switch)
```

**State Management (Know This Flow):**
```
App.jsx holds global state:
  - city, routeId (user selections)
  - offline (mode toggle)
  - activeTab (which tab)
  
useTransport hook holds request state:
  - transportData, metadata (API response)
  - loading, error (request status)
  - auto-refresh every 30s
  
Each component receives props from App
Each component is stateless (pure)
```

**Key Hook: `useTransport`**
```javascript
const { transportData, metadata, loading, error } = useTransport({
  city, routeId, offline, autoRefreshMs: 30000
});
```

**How It Works:**
1. Fetch data when inputs (city, routeId, offline) change
2. Auto-refresh every 30 seconds
3. Handle loading/error states
4. Cleanup timer when component unmounts

**API Service:**
```javascript
apiService.getTransportData({ city, routeId, offline })
→ HTTP GET /api/v1/transport?city=...&routeId=...&offline=...
→ Returns { data, metadata, _links }
```

**Practice:**
- Find where `VehicleMap` component receives `vehicles` prop
- Trace how `metadata.dataSource` becomes the colored badge
- Understand where the 30-second refresh is triggered

#### Step 3.2: Backend Controllers Layer (30 min)

**Read:** [explanation-backend.md](./explanation-backend.md) - Section 7

**Two Controllers:**

1. **TransportController** — Main API
   ```java
   @RestController
   @RequestMapping("/api/v1/transport")
   public class TransportController {
       @GetMapping                          // GET /api/v1/transport
       @GetMapping("/vehicles")             // GET /api/v1/transport/vehicles
       @GetMapping("/arrivals")             // GET /api/v1/transport/arrivals
       @GetMapping("/alerts")               // GET /api/v1/transport/alerts
       @GetMapping("/plan")                 // GET /api/v1/transport/plan
       @GetMapping("/crowding")             // GET /api/v1/transport/crowding
   }
   ```

2. **CacheController** — Cache management
   ```java
   @GetMapping("/stats")    // Cache statistics
   @DeleteMapping("")       // Clear cache
   ```

**Request Flow:**
```
HTTP Request
  ↓
@RequestParam validation (Required params checked)
  ↓
Method calls service
  ↓
Service returns response
  ↓
Controller wraps in ResponseEntity
  ↓
HTTP Response (200 OK + JSON)
```

**Key Concept:**
- Controllers are thin (just validation + calling service)
- Business logic is in services (not controllers)
- All error handling is in `GlobalExceptionHandler`

#### Step 3.3: Backend Service Layer (1 hour) ⭐ CRITICAL

**Read:** [explanation-backend.md](./explanation-backend.md) - Section 4

**Three Main Services:**

1. **TransportService** — Orchestrator
   ```
   getTransportData(city, routeId, offline)
     ↓
   Stage 1: Is offline? → Return MOCK
   Stage 2: Fresh cache? → Return CACHE
   Stage 3: Live API? → Return LIVE
   Stage 4: Stale cache? → Return STALE_CACHE
   Stage 5: Return MOCK (fallback)
   ```

   Key Methods:
   - `getTransportData()` — Main entry point
   - `fetchFromApis()` — Call MTA or TransitLand
   - `selectClient(city)` — Pick right API client
   - `enrichData()` — Call AlertService

2. **AlertService** — Rule Evaluation
   ```
   evaluate(transportData)
     ↓
   Check delays > 15 min? → DELAY alert
   Check disruptions? → DISRUPTION alert
   Check crowding FULL? → CROWDING alert
   Check weather? → WEATHER alert
     ↓
   Return List<AlertMessage>
   ```

3. **RoutePlannerService** — Journey Planning
   ```
   plan(from, to, city, alerts, arrivals)
     ↓
   Calculate direct route
   Calculate alternative routes
   Score confidence (penalty for disruptions)
   Return List<RoutePlan>
   ```

**MockDataService** — Synthetic Data
```
getMockData(city, routeId)
  ↓
Load from JSON files (src/main/resources/mock-data/)
OR generate synthetic data
  ↓
Return TransportData
```

**Practice Questions:**
- "What happens if MTA times out after 5 seconds?"
- "How does system know when to use fresh vs stale cache?"
- "When does AlertService get called?"

#### Step 3.4: Cache Layer (1 hour) ⭐ CRITICAL

**Read:** [explanation-backend.md](./explanation-backend.md) - Section 9

**Two Components:**

1. **InMemoryCache<K, V>** — The actual cache
   ```java
   // Thread-safe generic cache
   ConcurrentHashMap<K, CacheEntry<V>> store
   
   get(key) → If fresh (< TTL) return data, else return empty
   getStale(key) → If stale (< stale-TTL) return data, else return empty
   put(key, value) → Store with timestamp
                     If full, evict LRU entry
   
   Background thread:
     Every 60s, scan and remove entries > stale-TTL
   ```

2. **CacheService** — Spring wrapper
   ```java
   // Reads config from application.properties
   TTL = 300 seconds (5 minutes)
   Stale-TTL = 3600 seconds (1 hour)
   Max size = 1000 entries
   
   Methods:
   - get(key) → returns Optional
   - put(key, value) → stores with TTL
   - getStale(key) → fallback
   - clear() → remove all
   
   Logs stats every 5 minutes (hits, misses, evictions)
   ```

**Key Insight:**
```
get("nyc:a") 
  ├─ Hit (age < 300s)? → Return with age
  └─ Miss → Return Optional.empty()

getStale("nyc:a")
  ├─ Hit (age < 3600s)? → Return with stale flag
  └─ Miss → Return Optional.empty()
```

**TTL Visualization:**
```
Time
0s    ← put() called
↓
300s  ← fresh-TTL expires
      ← get() now returns empty
      ← getStale() still returns data (and is used)
↓
3600s ← stale-TTL expires
      ← getStale() returns empty
      ← Eviction thread removes from cache
```

**Practice:**
- Draw the cache lifecycle timeline
- Understand LRU (Least Recently Used) eviction
- Know exactly when each TTL applies

#### Step 3.5: API Client Layer (1 hour)

**Read:** [explanation-backend.md](./explanation-backend.md) - Section 6

**Interface Contract:**
```java
public interface TransitApiClient {
    List<VehicleLocation> fetchVehicleLocations(city, routeId);
    List<ArrivalPrediction> fetchArrivalPredictions(stopId, routeId);
    List<ServiceAlert> fetchServiceAlerts(city);
    List<RoutePlan> fetchRoutePlans(from, to, city);
    List<CrowdingInfo> fetchCrowdingInfo(routeId);
    boolean isAvailable();
    String getProviderName();
}
```

**Three Implementations:**

1. **MtaApiClient** (NYC)
   ```
   API: https://bustime.mta.info/api/siri/vehicle-monitoring.json
   Format: SIRI JSON (nested structure)
   Timeout: 5 seconds
   Parsers: parseMtaVehicles(), parseMtaArrivals(), parseMtaAlerts()
   ```

2. **TransitLandApiClient** (Fallback)
   ```
   API: https://transit.land/api/v2/rest
   Format: GTFS REST API
   Provider: NYC MTA (operator_id: o-dr5r-nyct)
   Timeout: 5 seconds
   ```

3. **SeptaApiClient** (Philly)
   ```
   API: Philadelphia SEPTA endpoints
   Format: SEPTA specific
   Timeout: 5 seconds
   ```

**How Selection Works:**
```java
private TransitApiClient selectClient(String city) {
    if (city.equals("nyc")) return mtaClient;
    if (city.equals("philly")) return septaClient;
    return transitLandClient; // fallback
}
```

**Error Handling:**
```
HTTP Request
  ↓
Timeout? (5 sec) → Throw TransitApiException
Response status 5xx? → Throw TransitApiException
Network error? → Throw TransitApiException
  ↓
Service catches → Tries next provider
All fail? → Falls back to cache/mock
```

**Parser Example (MTA):**
```
Raw JSON from MTA:
{
  "Siri": {
    "ServiceDelivery": {
      "VehicleMonitoringDelivery": [
        { "VehicleActivity": [...] }
      ]
    }
  }
}

parseMtaVehicles():
  1. Navigate nested structure
  2. Extract coordinates
  3. Calculate delay from scheduled/actual times
  4. Map to VehicleLocation model
  5. Return List<VehicleLocation>
```

**Practice:**
- Understand SIRI format (nested structure)
- Know timeout values (5s for sync, 8s for client)
- Trace error handling path

---

## PHASE 4: Hands-On Practice (2 hours)

### What You'll Learn
Apply your knowledge by running code, making changes, and testing.

### Practice Exercises

#### Exercise 4.1: Trace a Request End-to-End (30 min)

**Scenario:** User clicks "Get Transit Data" for NYC Route A

**Trace Steps:**
```
1. Frontend: App.jsx
   - Click triggers useTransport hook
   - city = "nyc", routeId = "A", offline = false

2. useTransport Hook
   - Calls apiService.getTransportData({ city: "nyc", routeId: "A" })

3. apiService.js
   - Constructs URL: /api/v1/transport?city=nyc&routeId=A
   - Calls fetch(url)
   - 8-second timeout set

4. Spring Boot Backend
   - TransportController.getTransportData("nyc", "A", null)
   - Calls transportService.getTransportData("nyc", "A", false)

5. TransportService (5-Stage Chain)
   - Stage 1: offline? No
   - Stage 2: cache hit? Check cacheService.get("nyc:a")
     - If HIT → Return CACHE, stop
   - Stage 3: Try APIs
     - selectClient("nyc") → mtaClient
     - fetchVehicleLocations("nyc", "A")
     - Timeout 5s
     - If SUCCESS → Cache & return LIVE
     - If FAIL → Continue
   - Stage 4: getStale("nyc:a")
     - If HIT → Return STALE_CACHE
     - If MISS → Continue
   - Stage 5: getMockData("nyc", "A")
     - Return MOCK

6. Service Enrichment
   - AlertService.evaluate(transportData)
   - Returns List<AlertMessage>

7. Response Building
   - Build TransportResponse
   - Add metadata (dataSource, cacheAge, timestamp)
   - Add HATEOAS links
   - Return 200 OK + JSON

8. Frontend
   - React receives response
   - Updates state: transportData, metadata
   - Components re-render
   - User sees vehicles, alerts, badge
```

**Practice:** Walk through this path in the code

#### Exercise 4.2: Add a New Alert Rule (45 min)

**Goal:** Add a rule "Alert if vehicle is more than 30 minutes late"

**Steps:**

1. **Understand Current Rules** (AlertService.java)
   ```java
   private void evaluateVehicleDelays(TransportData data, List<AlertMessage> alerts) {
       for (VehicleLocation vehicle : data.getVehicles()) {
           if (vehicle.getDelaySeconds() > 900) {  // 15 minutes
               alerts.add(new AlertMessage(
                   "DELAY",
                   "WARNING",
                   "Significant delays - Plan accordingly",
                   "clock-alert"
               ));
               break;
           }
       }
   }
   ```

2. **Add New Rule**
   ```java
   private void evaluateSevereDelays(TransportData data, List<AlertMessage> alerts) {
       for (VehicleLocation vehicle : data.getVehicles()) {
           if (vehicle.getDelaySeconds() > 1800) {  // 30 minutes
               alerts.add(new AlertMessage(
                   "SEVERE_DELAY",
                   "ERROR",
                   "Severe delays - Consider alternatives",
                   "alert-triangle"
               ));
               break;
           }
       }
   }
   ```

3. **Call from evaluate() method**
   ```java
   public List<AlertMessage> evaluate(TransportData data) {
       List<AlertMessage> alerts = new ArrayList<>();
       evaluateVehicleDelays(data, alerts);
       evaluateSevereDelays(data, alerts);  // ← Add here
       evaluateServiceDisruptions(data, alerts);
       // ... etc
       return alerts;
   }
   ```

4. **Write Test**
   ```java
   @Test
   void testSevereDelay_above30Minutes_triggersAlert() {
       TransportData data = TransportData.builder()
           .vehicles(List.of(
               VehicleLocation.builder()
                   .vehicleId("V1")
                   .delaySeconds(1900)  // 31+ minutes
                   .build()
           ))
           .build();
       
       List<AlertMessage> alerts = alertService.evaluate(data);
       
       assertTrue(alerts.stream()
           .anyMatch(a -> a.getType().equals("SEVERE_DELAY")));
   }
   ```

5. **Run & Verify**
   ```bash
   cd backend
   ./gradlew test
   ```

**What You Learn:**
- How to modify service logic
- How to write tests
- How alerts flow to frontend

#### Exercise 4.3: Test Cache Behavior (30 min)

**Goal:** Verify cache TTL and stale behavior

```bash
# Terminal 1: Start backend
docker-compose up backend

# Terminal 2: Test cache

# 1. Initial request (no cache)
curl http://localhost:8080/api/v1/transport?city=nyc | jq .metadata.dataSource
# Output: "LIVE"

# 2. Immediate second request (fresh cache)
curl http://localhost:8080/api/v1/transport?city=nyc | jq .metadata.dataSource
# Output: "CACHE"

# 3. Check cache stats
curl http://localhost:8080/api/v1/cache/stats | jq
# Notice: hits: 1, misses: 1

# 4. Clear cache
curl -X DELETE http://localhost:8080/api/v1/cache
# Output: { "message": "Cache cleared", "entriesDeleted": 1 }

# 5. Request again
curl http://localhost:8080/api/v1/transport?city=nyc | jq .metadata.dataSource
# Output: "LIVE" (fresh fetch)

# 6. Check stats again
curl http://localhost:8080/api/v1/cache/stats | jq
# Notice: hits still 1, misses now 2
```

**What You Learn:**
- Cache lifecycle in practice
- How to verify TTL behavior
- How to use cache endpoints

#### Exercise 4.4: Offline Mode Testing (15 min)

**Goal:** Test system behavior when APIs are unavailable

```bash
# Test offline mode flag
curl "http://localhost:8080/api/v1/transport?city=nyc&offline=true" | jq

# Notice:
# - dataSource: "MOCK"
# - offlineMode: true
# - Response instant (no API call)
# - Data is synthetic but realistic

# Compare to live mode
curl "http://localhost:8080/api/v1/transport?city=nyc&offline=false" | jq

# Notice:
# - dataSource: "LIVE" or "CACHE"
# - offlineMode: false
# - May take slightly longer (API call)
```

**What You Learn:**
- How offline flag works
- Mock data structure
- System resilience in action

---

## PHASE 5: Presentation Preparation (1 hour)

### What You'll Learn
How to structure your seminar presentation.

### Presentation Outline

#### Opening (5 min)
**Hook:** "What if you're waiting for the train and the transit app crashes?"

**Problem Statement:**
- Users need real-time transit info (location, ETA, alerts, capacity, routing)
- Transit APIs are unreliable (downtime, slow, errors)
- Solution: System that NEVER completely fails

**Show Demo:**
- Open frontend at http://localhost
- Toggle offline mode
- Show it still works with mock data

#### Section 1: Architecture Overview (10 min)

**Visual:** Show the architecture diagram
```
Frontend → Backend → APIs (MTA, TransitLand, SEPTA)
Backend also has:
  ├─ Cache (in-memory, TTL)
  ├─ Services (business logic)
  └─ Controllers (REST endpoints)
```

**Key Points:**
- React frontend (no heavy libraries)
- Spring Boot backend (minimal dependencies)
- Custom cache (not Redis)
- Multiple API providers (redundancy)

**Show Diagram:** [ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md#overall-architecture)

#### Section 2: The 5-Stage Degradation Chain (15 min) ⭐

**THIS IS THE CORE - SPEND TIME HERE**

**Visual:** Draw/show the 5-stage flow

**Explain Each Stage:**
1. **Offline Mode** — For development/demos
2. **Fresh Cache** — Fast responses (< 5 min old)
3. **Live API** — When everything works
4. **Stale Cache** — Emergency fallback (< 1 hour old)
5. **Mock Data** — Final safety net

**Live Demo:**
```bash
# Terminal 1
docker-compose up

# Terminal 2
# 1. First request (LIVE)
curl http://localhost:8080/api/v1/transport?city=nyc | jq .metadata.dataSource

# 2. Immediate request (CACHE)
curl http://localhost:8080/api/v1/transport?city=nyc | jq .metadata.dataSource

# 3. Offline mode (MOCK)
curl "http://localhost:8080/api/v1/transport?city=nyc&offline=true" | jq .metadata
```

**Key Insight:** "The user ALWAYS gets data. We just tell them how fresh it is."

#### Section 3: Design Patterns (10 min)

**Show:** Real code examples for each pattern

**Pattern 1: Chain of Responsibility**
```java
// Show TransportService flow through 5 stages
```

**Pattern 2: Strategy**
```java
// Show TransitApiClient interface + implementations
```

**Pattern 3: Adapter**
```java
// Show MTA SIRI → VehicleLocation conversion
```

**Pattern 4: Observer**
```javascript
// Show useTransport auto-refresh
```

**Pattern 5 & 6:** Brief mention

**Key Slide:** "Using proven patterns makes code maintainable and testable"

#### Section 4: Frontend Experience (10 min)

**Show Live Demo:**
- Open http://localhost
- Switch between tabs (Live View, Plan Journey)
- Toggle offline mode
- Change city/route
- Explain what each UI element shows

**Code Walkthrough:**
- Show App.jsx structure
- Show useTransport hook
- Show API service
- Show a component (e.g., VehicleMap)

**Key Point:** "React components are simple and composable"

#### Section 5: Backend Deep Dive (15 min)

**API Endpoints Overview:**
- Show Swagger UI: http://localhost:8080/swagger-ui
- List 8 endpoints
- Show request/response format

**Service Layer:**
- TransportService (5-stage logic)
- AlertService (rule evaluation)
- RoutePlannerService (journey planning)

**Cache System:**
- TTL behavior
- Stale-TTL fallback
- LRU eviction

**API Clients:**
- How system handles multiple providers
- Error handling
- Parser logic

**Show Code:** TransportService.java (main orchestrator)

#### Section 6: Testing & Quality (5 min)

**Testing Strategy:**
- Unit tests (isolated components)
- Service tests (with mocks)
- Integration tests (end-to-end)

**Coverage:**
- Cache logic: 100%
- Service logic: 95%+
- Controllers: 90%+

**Show:** Running tests
```bash
cd backend
./gradlew test
```

#### Section 7: Deployment (5 min)

**Docker:**
- Multi-stage build
- Minimal image size
- Health checks

**CI/CD:**
- GitHub Actions
- Jenkins pipeline

**Production Ready:**
- Security headers
- Error handling
- Monitoring

#### Closing (5 min)

**Summary:**
- Problem: Unreliable APIs
- Solution: 5-stage degradation
- Result: System that NEVER fails
- Technology: Clean, minimal, patterns-based

**Key Takeaway:** "Elegant architecture makes software reliable and maintainable"

---

## PHASE 6: Q&A Preparation (1 hour)

### What You'll Learn
How to answer any technical question about the codebase.

### Common Questions & Answers

#### Architecture Questions

**Q: Why not use Redis for caching?**
A: "We use an in-memory cache to have fine-grained control over the 5-stage degradation chain. Redis doesn't have built-in 'stale-TTL' concept. For this specific use case (always provide data), custom cache is better. In production at scale (1M+ requests/day), we'd add Redis for distributed caching."

**Q: Why build custom HTTP client instead of using a library like Retrofit?**
A: "Minimal dependencies = smaller app, fewer vulnerabilities. Java's built-in HttpClient (since Java 11) is sufficient and clean. For this use case, we don't need the extra features of Retrofit. We control exactly how timeouts and retries work."

**Q: Why not use a real map library (Leaflet, Google Maps)?**
A: "Adds 200-500KB to download. Our schematic map (vehicle positions on a line) doesn't need geographic accuracy. SVG is lightweight and built-in. If we needed true maps, we'd use Leaflet."

---

#### Cache & Resilience Questions

**Q: What happens if all APIs fail?**
A: "
1. First, we try stale cache (up to 1 hour old)
2. If nothing in stale cache, we return mock data
3. User gets data with metadata indicating source (STALE_CACHE or MOCK)
4. Frontend shows warning banner
5. System never returns an error
"

**Q: How long can cache be used?**
A: "
- Fresh cache: 5 minutes (TTL)
- Stale cache (emergency): 1 hour (stale-TTL)
- After 1 hour with no APIs, we return mock data
- Configuration is in application.properties
"

**Q: What if cache gets too big?**
A: "
- Max size: 1000 entries (configurable)
- When full, we evict Least Recently Used (LRU) entry
- Background thread removes entries > stale-TTL every minute
- For 1M users, we'd use Redis (distributed cache)
"

---

#### API & Frontend Questions

**Q: Why use HATEOAS/HAL envelope?**
A: "
- Self-documenting: Response includes links to related resources
- Frontend doesn't hardcode URLs
- API is discoverable
- Makes API versioning easier
"

**Q: How does auto-refresh work?**
A: "
- useTransport hook sets 30-second interval
- Every 30s, fetches fresh data
- If API slower than 30s, we get multiple overlapping requests
- When component unmounts, we clear the interval (prevent leaks)
"

**Q: Why no global state management library (Redux)?**
A: "
- For this app, complexity isn't warranted
- App.jsx holds simple state (city, route, tab)
- useTransport hook handles data fetching
- Unidirectional data flow is easy to understand
- If app grows to 50+ components, add Redux then
"

---

#### Business Logic Questions

**Q: What's the 5-stage degradation chain?**
A: "
1. Offline mode? Return MOCK instantly (for dev/demos)
2. Fresh cache (<5 min)? Return CACHE (fast, reliable)
3. Live API working? Return LIVE (fetch new data)
4. APIs down but have stale cache (<1 hr)? Return STALE_CACHE + warning
5. Everything else? Return MOCK (never fail)

User always gets data. Metadata tells them how fresh it is.
"

**Q: When should offline mode be used?**
A: "
- Development (without API keys)
- Product demos (without internet)
- Testing (predictable data)
- Not for production (use caching instead)
"

**Q: How does system handle two API failures simultaneously?**
A: "
- TransportService tries MTA first
- If MTA times out or errors, tries TransitLand
- If TransitLand also fails, checks stale cache
- If no stale cache, returns mock data
- Each failure is logged
"

---

#### Testing Questions

**Q: How do you test API failures?**
A: "
- Use Mockito to mock the API client
- Make the mock throw TransitApiException
- Verify service falls back to cache/mock
- No real API calls during tests
"

**Q: How do you test cache TTL expiration?**
A: "
- Create cache with very short TTL (1 second)
- Put data in cache
- Wait TTL
- Verify get() returns empty, getStale() returns data
"

**Q: How do you test the frontend auto-refresh?**
A: "
- Use Jest fake timers (jest.useFakeTimers())
- Verify fetch is called on mount
- Advance time 30 seconds
- Verify fetch called again
- Clean up timers
"

---

#### Deployment Questions

**Q: What's the minimum infrastructure needed?**
A: "
- 2 Docker containers (frontend + backend)
- Internet connection (to call MTA/TransitLand APIs)
- 500MB RAM (backend), 100MB RAM (frontend)
- No database needed
"

**Q: How do you deploy to Kubernetes?**
A: "
- Build images: docker build -t myregistry/transport-backend:latest .
- Push to registry: docker push ...
- Deploy with kubectl: kubectl apply -f k8s/deployment.yaml
- Stateless design makes K8s scaling simple
"

**Q: What are health checks for?**
A: "
- Docker periodically checks /actuator/health
- If app unresponsive, Docker restarts it
- K8s uses same endpoint to determine pod readiness
- Keeps system available even if individual containers fail
"

---

#### Data & Integration Questions

**Q: How does MTA API format differ from TransitLand?**
A: "
- MTA: SIRI format (nested JSON, proprietary)
- TransitLand: GTFS REST API (standard transit format)
- System abstracts both via TransitApiClient interface
- Each client has parser to convert to VehicleLocation, etc.
"

**Q: What if MTA changes their API format?**
A: "
- Update MtaApiClient parser
- Rest of system unaffected (uses interface)
- This is the Adapter pattern in action
"

**Q: How many cities can system support?**
A: "
- Currently: NYC, Philadelphia
- Framework supports unlimited cities
- Just add new API client + selectClient() logic
- Each city's data stored separately in cache (cache key = city:route)
"

---

#### Performance Questions

**Q: How fast is the system?**
A: "
- Cache hit: <50ms (in-memory)
- Live API call: 5-8 seconds (API timeout 5s + processing 1-3s)
- Offline mode: <10ms
"

**Q: Can it handle 1M users?**
A: "
- Current setup: ~1000 concurrent requests
- Limiting factor: APIs (MTA, TransitLand)
- Solution: Add Redis cache layer (distribute cache across servers)
- Use load balancer (nginx, HAProxy)
- Possibly add API rate limiting
"

**Q: How often does cache refresh?**
A: "
- Auto-refresh: Frontend fetches every 30 seconds
- Cache TTL: 5 minutes (300 seconds)
- So typical user sees fresh data within 5 minutes
- Can force refresh by clearing cache endpoint
"

---

#### Security Questions

**Q: Is API public or does it need authentication?**
A: "
- Currently public (for assessment)
- Production would add:
  - API key header (Authorization: Bearer KEY)
  - Rate limiting (100 req/min per IP)
  - SSL/TLS (HTTPS only)
  - CORS restrictions
"

**Q: How are API keys handled?**
A: "
- Keys stored in environment variables (not code)
- Example: TRANSITLAND_API_KEY=...
- Never commit keys to version control
- Docker-compose pulls from .env file
"

**Q: What are the security headers?**
A: "
- X-Content-Type-Options: nosniff (prevents MIME sniffing)
- X-Frame-Options: DENY (prevents clickjacking)
- CSRF protection disabled (stateless API, safe)
- Configured in SecurityConfig.java
"

---

### Practice Q&A Session

**Do This:**
1. Have a colleague ask you random questions from above
2. Practice 1-minute answers (not too long)
3. Use real code examples when explaining
4. Always explain the "why" not just the "what"

---

## Study Checklist

### ✅ Must Know (Critical)
- [ ] The 5-stage degradation chain (memorize, can draw)
- [ ] 6 design patterns (name and purpose)
- [ ] 8 API endpoints (paths, purpose, response structure)
- [ ] Service layer architecture (TransportService, AlertService)
- [ ] Cache TTL and stale-TTL concept
- [ ] How frontend auto-refresh works

### ✅ Should Know (Important)
- [ ] Every data model (VehicleLocation, ArrivalPrediction, etc.)
- [ ] How API clients work (MTA, TransitLand)
- [ ] Error handling flow
- [ ] How to read the codebase
- [ ] How tests are structured

### ✅ Nice to Know (Context)
- [ ] Docker multi-stage build
- [ ] CI/CD pipeline
- [ ] Deployment to K8s
- [ ] Performance characteristics
- [ ] Security considerations

---

## Practice Scenarios

### Scenario 1: Live Coding Demo
```
Show: Draw the 5-stage chain on whiteboard
      As each stage, show corresponding code

"Stage 1: Check offline"
→ Show TransportService line: if (isOffline) return MOCK

"Stage 2: Check cache"
→ Show cacheService.get(cacheKey)

"Stage 3: Call API"
→ Show selectClient(city).fetchVehicleLocations(...)

etc.
```

### Scenario 2: Architecture Question
```
Q: "How does this handle API failures?"

A: "Great question. We have a 5-stage fallback chain...
   [Explain 5 stages]
   
   The key insight is the user ALWAYS gets data. We just
   tell them how fresh it is via metadata.dataSource.
   
   Want to see it in action?
   [Demo: toggle offline, show MOCK data]"
```

### Scenario 3: Code Walkthrough
```
Q: "Walk me through what happens when I click search"

A: "Of course. Let me show you the flow:
   
   [Open App.jsx]
   User enters city='nyc', this triggers useTransport hook
   
   [Open useTransport.js]
   Hook calls apiService.getTransportData()
   
   [Open apiService.js]
   Service builds URL and sends HTTP request
   
   [Show swagger UI]
   Request hits POST /api/v1/transport
   
   [Open TransportController]
   Controller validates params and calls service
   
   [Open TransportService]
   Here's the 5-stage chain...
   
   [Follow to end]
   Response wraps in TransportResponse and returns"
```

---

## 1-Hour Seminar Outline

**Total Time: 60 minutes**

- Opening: 5 min
- Architecture: 10 min
- 5-Stage Chain: 15 min ← Most important
- Design Patterns: 10 min
- Frontend/Backend: 10 min
- Q&A: 10 min

---

## Final Preparation (Day Before)

1. **Run the system end-to-end**
   ```bash
   docker-compose up --build
   # Verify frontend loads
   # Verify APIs work
   # Verify cache works
   # Verify offline mode works
   ```

2. **Prepare slides/visuals**
   - Architecture diagram
   - 5-stage degradation flow
   - Design patterns

3. **Test your demo commands**
   ```bash
   curl http://localhost:8080/api/v1/transport?city=nyc | jq
   curl -X DELETE http://localhost:8080/api/v1/cache
   ```

4. **Practice 1-minute explanations** of:
   - The 5-stage chain
   - Each design pattern
   - How caching works
   - API integration

5. **Read through common Q&As** above

---

## Day Of Seminar

**30 min before:**
- Start docker-compose
- Verify everything works
- Do quick mental walkthrough of presentation
- Test projector/screen

**During:**
- Speak clearly, don't rush
- Use code examples
- Do live demos (nothing beats seeing it work)
- Engage audience with questions
- Be honest when you don't know something

**After:**
- Offer to answer follow-up questions
- Share documentation GitHub links
- Welcome code review requests

---

## Success Criteria

**By end of seminar, audience should understand:**

✅ The problem (unreliable APIs)  
✅ The solution (5-stage degradation)  
✅ The architecture (layered, patterns-based)  
✅ How to use the system (frontend demo)  
✅ How it works under the hood (code walkthrough)  
✅ Why it was designed this way (design rationale)  

**You should be able to:**

✅ Draw the 5-stage chain from memory  
✅ Name and explain 6 design patterns  
✅ Walk through code flow for any request  
✅ Explain cache behavior (TTL, stale-TTL)  
✅ Answer any technical question  
✅ Handle "what-ifs" (API fails, cache full, etc.)  

---

**Good luck with your seminar! You've got this.** 🚀

Remember: The 5-stage degradation chain is the star of the show. If you thoroughly understand that and can explain it clearly, you'll ace your presentation.
