# Getting Started & Development Guide

**Last Updated:** May 7, 2026  
**For:** New developers joining the project  
**Time to Read:** 15 minutes

---

## Quick Start (5 minutes)

### Prerequisites

- Docker & Docker Compose installed
- Git installed
- A modern web browser
- (Optional) Java 17 + Gradle for local backend development

### Start Everything

```bash
# Clone repository (if needed)
git clone <repo-url>
cd public\ transport\ tracker\ service

# Start all services
docker-compose up --build

# Wait for output like:
# backend_1   | Started TransportTrackerApplication in X seconds
# frontend_1  | Listening on port 3000
```

### Verify It's Working

**Frontend:** Open http://localhost (or http://localhost:80)
- You should see a transit tracker dashboard
- Try selecting a city and route
- Watch real-time vehicle data appear

**Backend APIs:** In another terminal:
```bash
# Get real-time transit data
curl http://localhost:8080/api/v1/transport?city=nyc&routeId=A

# Get vehicle positions
curl http://localhost:8080/api/v1/transport/vehicles?city=nyc

# Get arrivals at Times Square
curl "http://localhost:8080/api/v1/transport/arrivals?stopId=A27N"

# Check cache stats
curl http://localhost:8080/api/v1/cache/stats
```

**Swagger UI:** http://localhost:8080/swagger-ui/index.html
- Interactive API documentation
- Try endpoints without curl

---

## Project Structure Overview

```
.
├── backend/                          ← Java Spring Boot application
│   ├── build.gradle                  ← Build configuration
│   ├── src/main/java/com/transport/tracker/
│   │   ├── TransportTrackerApplication.java
│   │   ├── config/                   ← Spring configurations
│   │   ├── controller/               ← REST endpoints
│   │   ├── service/                  ← Business logic
│   │   ├── client/                   ← API clients (MTA, TransitLand, SEPTA, TfL)
│   │   ├── cache/                    ← Caching system
│   │   ├── model/                    ← Data models
│   │   ├── exception/                ← Custom exceptions
│   │   ├── health/                   ← Health indicators
│   │   ├── metrics/                  ← Monitoring
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       ├── application-prod.properties
│   │       └── mock-data/            ← Sample JSON data
│   └── src/test/java/               ← Unit tests
│
├── frontend/                         ← React application
│   ├── package.json                  ← Dependencies
│   ├── public/index.html             ← Single HTML file
│   └── src/
│       ├── App.jsx                   ← Main component
│       ├── App.css
│       ├── index.js                  ← Entry point
│       ├── services/
│       │   └── apiService.js         ← Backend communication
│       ├── hooks/
│       │   └── useTransport.js       ← Data fetching logic
│       └── components/               ← Reusable UI components
│           ├── RouteSearch/
│           ├── AlertBanner/
│           ├── ArrivalBoard/
│           ├── VehicleMap/
│           ├── CrowdingIndicator/
│           ├── RoutePlanner/
│           └── OfflineToggle/
│
├── docs/                             ← Documentation
│   ├── API.md                        ← Complete API reference
│   ├── explanation-backend.md        ← Backend architecture
│   ├── explanation-frontend.md       ← Frontend guide
│   ├── explanation-business-logic.md ← Business logic deep dive
│   ├── sequence-diagram.md           ← Interaction diagrams
│   ├── COMPLETE_PROJECT_DOCUMENTATION.md
│   ├── ARCHITECTURE_AND_PATTERNS.md  ← Design patterns
│   ├── IMPLEMENTATION_STATUS.md      ← What's built
│   └── design-patterns.md
│
├── docker-compose.yml                ← Multi-container setup
├── Dockerfile (backend)              ← Backend image
├── Dockerfile (frontend)             ← Frontend image
├── Jenkinsfile                       ← CI/CD pipeline
└── README.md
```

---

## Key Files to Know

### Backend Entry Points

| File | Purpose | How to Find It |
|------|---------|----------------|
| `TransportTrackerApplication.java` | Application startup | `backend/src/main/java/.../TransportTrackerApplication.java` |
| `TransportController.java` | All API endpoints | `.../controller/TransportController.java` |
| `TransportService.java` | Core business logic (5-stage chain) | `.../service/TransportService.java` |
| `application.properties` | Configuration | `backend/src/main/resources/application.properties` |

### Frontend Entry Points

| File | Purpose | How to Find It |
|------|---------|----------------|
| `App.jsx` | Main component (layout) | `frontend/src/App.jsx` |
| `apiService.js` | Backend communication | `frontend/src/services/apiService.js` |
| `useTransport.js` | Data fetching hook | `frontend/src/hooks/useTransport.js` |

---

## Understanding the 5-Stage Degradation Chain

This is THE core concept of the application. Every request goes through this:

```
Request comes in
    ↓
Stage 1: Offline Mode?
    → YES: Return MOCK data immediately
    → NO: Continue
    ↓
Stage 2: Fresh Cache? (< 300 seconds old)
    → YES: Return CACHE data (fast!)
    → NO: Continue
    ↓
Stage 3: Try Live API
    → SUCCESS: Cache it, return LIVE data
    → FAILURE: Continue
    ↓
Stage 4: Stale Cache? (< 3600 seconds old)
    → YES: Return STALE_CACHE data (with warning)
    → NO: Continue
    ↓
Stage 5: Return MOCK data (fallback)
```

**Metaphor:** Like a restaurant:
1. **Offline** = "Kitchen is closed, use the pre-made plate"
2. **Fresh Cache** = "Just made this 2 min ago, plate is hot"
3. **Live API** = "Calling chef now for fresh order"
4. **Stale Cache** = "Made this 30 min ago, still OK"
5. **Mock** = "Here's the sample menu item picture"

**Key insight:** The user ALWAYS gets data. We just tell them how fresh it is.

---

## How to Make Changes

### Backend Changes

#### Adding a New Endpoint

1. Add method to `TransportController`:
```java
@GetMapping("/my-endpoint")
public ResponseEntity<TransportResponse<?>> myEndpoint(
        @RequestParam String city) {
    // Call service
    MyData result = transportService.getMyData(city);
    // Wrap response
    return ResponseEntity.ok(wrapResponse(result, "LIVE"));
}
```

2. Add method to `TransportService`:
```java
public MyData getMyData(String city) {
    // Apply degradation chain
    // Fetch from APIs or cache
    // Return result
}
```

3. Write test in `TransportControllerTest`:
```java
@Test
void testMyEndpoint_ReturnsData() {
    ResponseEntity<?> response = restTemplate.getForEntity("/api/v1/my-endpoint?city=nyc", ...);
    assertEquals(200, response.getStatusCodeValue());
}
```

#### Adding a New Alert Rule

Edit `AlertService.java`:
```java
private void evaluateMyRule(TransportData data, List<AlertMessage> alerts) {
    if (someCondition(data)) {
        alerts.add(new AlertMessage(
            "MY_ALERT",
            "WARNING",
            "My alert message",
            "my-icon"
        ));
    }
}
```

Call it from `evaluate()`:
```java
public List<AlertMessage> evaluate(TransportData data) {
    List<AlertMessage> alerts = new ArrayList<>();
    evaluateVehicleDelays(data, alerts);
    evaluateMyRule(data, alerts);  // ← Add here
    return alerts;
}
```

### Frontend Changes

#### Adding a New Component

1. Create folder: `frontend/src/components/MyComponent/`
2. Create files:
   - `MyComponent.jsx` (logic)
   - `MyComponent.css` (styles)

3. Example component:
```jsx
// MyComponent.jsx
export default function MyComponent({ data }) {
  return (
    <div className="my-component">
      <h2>{data.title}</h2>
      <p>{data.description}</p>
    </div>
  );
}
```

4. Import and use in `App.jsx`:
```jsx
import MyComponent from './components/MyComponent/MyComponent';

export default function App() {
  return (
    <>
      <RouteSearch />
      <MyComponent data={transportData} />  {/* Add here */}
    </>
  );
}
```

#### Modifying a Component

1. Edit the `.jsx` file (logic)
2. Edit the `.css` file (appearance)
3. Test in browser (http://localhost)

---

## Running Tests

### Backend Tests

```bash
cd backend

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests InMemoryCacheTest

# Run with output
./gradlew test --info
```

**Test files location:** `backend/src/test/java/com/transport/tracker/...`

### Frontend Tests (If Added)

```bash
cd frontend
npm test
```

---

## Debugging

### Backend Debugging

#### Option 1: Print Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(MyClass.class);

public void myMethod() {
    log.debug("Debug message: {}", variable);
    log.info("Info message");
    log.warn("Warning: {}", issue);
    log.error("Error:", exception);
}
```

**View logs:**
```bash
docker-compose logs backend
docker-compose logs backend -f  # Follow in real-time
```

#### Option 2: Remote Debugging

Start backend with debug flags:
```bash
cd backend
./gradlew bootRun --debug
```

Connect IDE debugger to `localhost:5005`

### Frontend Debugging

#### Browser DevTools

1. Open http://localhost in Chrome/Firefox
2. Press F12 (or right-click → Inspect)
3. **Console tab** — JavaScript errors
4. **Network tab** — API calls (check responses)
5. **React DevTools extension** — Component tree

#### Debugging Network Calls

```javascript
// In frontend code
console.log('API Response:', response);
console.log('Data source:', response.metadata.dataSource);
console.log('Cache age:', response.metadata.cacheAgeSeconds);
```

Then check Browser Console (F12).

---

## Environment Configuration

### Backend Configuration Files

**`application.properties`** — Default settings:
```properties
server.port=8080
transit.cache.ttl-seconds=300
transit.offline.enabled=false
```

**`application-dev.properties`** — Development overrides:
```properties
# Can add dev-specific settings
```

**`application-prod.properties`** — Production overrides:
```properties
# Stricter cache, security headers, etc.
```

### Environment Variables

Set these for API access:

```bash
# Optional (MTA works without it)
export MTA_API_KEY="your_mta_key"

# Required for TransitLand
export TRANSITLAND_API_KEY="your_transitland_key"

# Timeouts (in milliseconds)
export MTA_TIMEOUT_MS=5000
export TRANSITLAND_TIMEOUT_MS=5000

# Cache settings
export CACHE_TTL_SECONDS=300
export CACHE_STALE_TTL_SECONDS=3600

# Offline mode (true/false)
export OFFLINE_MODE=false
```

**In Docker Compose:**

Edit `docker-compose.yml`:
```yaml
services:
  backend:
    environment:
      - TRANSITLAND_API_KEY=${TRANSITLAND_API_KEY}
      - MTA_API_KEY=${MTA_API_KEY}
```

---

## Troubleshooting Common Issues

### "Port 8080 Already in Use"

```bash
# Find what's using port 8080
lsof -i :8080
# Kill it (replace 12345 with PID)
kill -9 12345
```

Or just use docker-compose (it's already isolated):
```bash
docker-compose up  # Handles port management
```

### "No Data Showing / All MOCK Data"

Check the data source badge:
- **LIVE** (green) — APIs are working
- **CACHE** (blue) — Recent cached data
- **STALE_CACHE** (yellow) — Old cached data, APIs down
- **MOCK** (gray) — APIs completely failed, no cache

**To debug:**

```bash
# Check if backend is running
curl http://localhost:8080/actuator/health

# Check if API clients work
curl http://localhost:8080/api/v1/cache/stats
```

### "Cache Not Updating"

The cache is working as designed:
- Data refreshes every 30 seconds (frontend auto-refresh)
- If you see same timestamp, cache hit is occurring (good!)
- Toggle offline mode OFF to force fresh API calls

### Docker Build Fails

```bash
# Clean and rebuild
docker-compose down
docker system prune -a
docker-compose up --build
```

### Tests Failing

```bash
# Make sure you're in the right directory
cd backend

# Clean build
./gradlew clean test

# Check Java version (must be 17+)
java -version
```

---

## Key Concepts to Remember

### 1. Metadata Field

Every API response includes metadata telling you:
- `dataSource` — LIVE, CACHE, STALE_CACHE, or MOCK
- `cacheAgeSeconds` — How old the cached data is (if cached)
- `timestamp` — When the response was generated
- `provider` — MTA, TransitLand, SEPTA, or TfL

### 2. Auto-Refresh

Frontend automatically fetches new data every 30 seconds:
- See fresh vehicle positions
- See updated delays and alerts
- No manual refresh needed

### 3. Offline Mode

Toggle on for testing without APIs:
- Returns instantly from mock data
- Useful for UI testing
- Useful for demos without internet

### 4. Error Resilience

System never completely fails:
- All APIs down? → Return stale cached data
- No cache available? → Return synthetic mock data
- User always sees something

---

## Common Development Workflows

### Workflow 1: Testing an API Endpoint

```bash
# 1. Start the system
docker-compose up

# 2. In another terminal, call the endpoint
curl "http://localhost:8080/api/v1/transport?city=nyc&routeId=A" | jq

# 3. Check the data source
# Look at metadata.dataSource field

# 4. Clear cache and call again (to force fresh data)
curl -X DELETE http://localhost:8080/api/v1/cache
curl "http://localhost:8080/api/v1/transport?city=nyc&routeId=A" | jq
```

### Workflow 2: Testing Cache Behavior

```bash
# 1. Get initial data (will be LIVE or MOCK)
curl "http://localhost:8080/api/v1/transport?city=nyc" | jq .metadata

# 2. Call again immediately (will be CACHE)
curl "http://localhost:8080/api/v1/transport?city=nyc" | jq .metadata

# 3. Check cache stats
curl http://localhost:8080/api/v1/cache/stats | jq

# 4. Wait 5+ minutes, call again (will refresh from API)
# Notice cache hit count increases
```

### Workflow 3: Frontend Development

```bash
# 1. Start backend
docker-compose up backend

# 2. In separate terminal, start frontend dev server
cd frontend
npm start

# 3. Frontend runs at http://localhost:3000 with hot reload
# Changes instantly visible as you edit

# 4. Press Ctrl+C to stop
```

---

## Next Steps

1. **Read the Documentation**
   - Start with [API.md](./API.md)
   - Then [ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md)

2. **Explore the Code**
   - Backend: `backend/src/main/java/com/transport/tracker/service/TransportService.java`
   - Frontend: `frontend/src/components/`

3. **Make a Small Change**
   - Add an alert rule in AlertService
   - Add a new component
   - Write a test

4. **Deploy**
   - Follow the deployment section below

---

## Deployment

### Local Docker Deployment

```bash
docker-compose up --build
# Frontend: http://localhost
# Backend: http://localhost:8080
# API Docs: http://localhost:8080/swagger-ui/index.html
```

### Production Deployment (Kubernetes)

```bash
# Build images
docker build -t myregistry/transport-backend:latest backend/
docker build -t myregistry/transport-frontend:latest frontend/

# Push to registry
docker push myregistry/transport-backend:latest
docker push myregistry/transport-frontend:latest

# Deploy with helm or kubectl
kubectl apply -f k8s/backend-deployment.yaml
kubectl apply -f k8s/frontend-deployment.yaml
```

### CI/CD with GitHub Actions

`.github/workflows/test-build-deploy.yml`:
```yaml
name: Test, Build, Deploy
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - run: cd backend && ./gradlew test
  build:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v2
      - uses: docker/build-push-action@v2
        with:
          context: ./backend
          push: true
          tags: myregistry/backend:latest
```

---

## Getting Help

### Documentation Files

- **[API.md](./API.md)** — Every endpoint explained
- **[explanation-backend.md](./explanation-backend.md)** — Backend internals
- **[explanation-frontend.md](./explanation-frontend.md)** — Frontend architecture
- **[ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md)** — Design decisions
- **[IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md)** — What's built, what's not

### Code Comments

Every major class has comments explaining:
```java
/**
 * TransportService — The core business logic.
 * 
 * Implements a 5-stage degradation chain:
 * 1. Offline mode → MOCK data
 * 2. Fresh cache → CACHE data
 * 3. Live API → LIVE data
 * 4. Stale cache → STALE_CACHE data
 * 5. Fallback → MOCK data
 */
```

### Swagger/OpenAPI

Interactive API documentation at:
http://localhost:8080/swagger-ui/index.html

Try any endpoint with the "Try it out" button.

---

## Quick Reference

| Task | Command |
|------|---------|
| Start everything | `docker-compose up --build` |
| Stop everything | `docker-compose down` |
| View backend logs | `docker-compose logs backend -f` |
| View frontend logs | `docker-compose logs frontend -f` |
| Run tests | `cd backend && ./gradlew test` |
| Clear cache | `curl -X DELETE http://localhost:8080/api/v1/cache` |
| Get vehicle data | `curl http://localhost:8080/api/v1/transport?city=nyc` |
| Get API docs | Open http://localhost:8080/swagger-ui/index.html |
| Frontend dev mode | `cd frontend && npm start` |

---

**Ready to code?** Start with the Getting Started section above, then pick a task and start!
