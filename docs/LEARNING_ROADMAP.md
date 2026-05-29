# Complete Learning Roadmap: Study & Code Understanding Path

**Created:** May 8, 2026  
**Purpose:** Guide developers from zero to expert understanding of the Public Transport Tracker codebase  
**Total Learning Time:** ~6-8 hours (depending on pace and depth)

---

## Quick Decision Tree

**Choose your starting point:**

```
┌─────────────────────────────────────────────────────────┐
│ What's your role?                                       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ 1. New Developer         → Start at FAST TRACK (1h)   │
│ 2. Backend Developer     → Start at BACKEND PATH (2h) │
│ 3. Frontend Developer    → Start at FRONTEND PATH (2h)│
│ 4. Architect/Tech Lead   → Start at ARCHITECT PATH (3h)│
│ 5. Deep Learner          → Start at DEEP DIVE (5h)    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Path 1: FAST TRACK (Get Running in 1 hour)

**Goal:** Understand the app, run it, see live data flowing.

### Phase 1A: Overview (10 min)
1. **Read:** [GETTING_STARTED.md](./GETTING_STARTED.md) — Section "What is this project"
   - ✅ Understand the 5-minute problem statement
   - ✅ Know what the app does

### Phase 1B: Run the Application (15 min)
1. **Follow:** [RUN_SERVERS.md](../RUN_SERVERS.md) — Step 1 & 2
   - Terminal 1: `cd backend && gradle bootRun`
   - Terminal 2: `cd frontend && npm install && npm start`
   - Wait for both to be ready
   - Open http://localhost:3000

### Phase 1C: Explore the UI (20 min)
1. **In the app:**
   - ✅ Try selecting "NYC" and route "A"
   - ✅ Watch the data source badge (should show "LIVE")
   - ✅ Note the vehicle positions on the map
   - ✅ See the arrival predictions
   - ✅ Observe the alerts banner

2. **Toggle offline mode:**
   - Click the offline toggle (📵)
   - Notice it changes to "MOCK"
   - See the data changes (same structure, different content)

3. **Check the API:**
   - Open `http://localhost:8080/api/v1/routes?city=nyc`
   - See the JSON response
   - Note the metadata field

### Phase 1D: Verify Working (15 min)
1. **Check backend logs:**
   - Look for: "TransitLand: Successfully received route data"
   - Look for: "Returning vehicles" and "Returning arrivals"

2. **Check backend health:**
   - Open `http://localhost:8080/actuator/health`
   - Should show "UP"

3. **Done! ✅**
   - You now understand what the app does
   - You've seen it working end-to-end

---

## Path 2: BACKEND DEVELOPER PATH (2 hours)

**Goal:** Understand the backend architecture, services, and API.

### Phase 2A: Setup (15 min)
1. **Complete FAST TRACK Path 1A-1B above**
2. **Verify Backend is Running:**
   ```
   curl http://localhost:8080/actuator/health
   ```
   Should return: `{"status":"UP"}`

### Phase 2B: API Reference (20 min)
1. **Read:** [docs/API.md](./API.md)
   - ✅ Understand all 8 endpoints
   - ✅ Know request parameters
   - ✅ See response format
   - ✅ Test one endpoint with curl

### Phase 2C: Backend Architecture (30 min)
1. **Read:** [docs/explanation-backend.md](./explanation-backend.md)
   - Section 1: Project Structure
   - Section 4: Configuration Layer
   - Section 5: Data Models
   - Section 6: API Clients
   - Section 7: Controllers

2. **In the code:** Open these files in IDE:
   - `backend/src/main/java/com/transport/tracker/model/` — Data classes
   - `backend/src/main/java/com/transport/tracker/controller/TransportController.java` — REST endpoints

### Phase 2D: Business Logic (25 min)
1. **Read:** [docs/explanation-business-logic.md](./explanation-business-logic.md)
   - Section 3: The 5-Stage Degradation Chain
   - Section 4: TransportService Explained
   - Section 5: AlertService Rule Evaluation

2. **In the code:** Open these files:
   - `backend/src/main/java/com/transport/tracker/service/TransportService.java`
   - `backend/src/main/java/com/transport/tracker/service/AlertService.java`
   - Find the methods: `getTransportData()` and `evaluateCondition()`

### Phase 2E: Caching System (20 min)
1. **Read:** [docs/explanation-backend.md](./explanation-backend.md) — Section 9
   - Understand TTL, stale-TTL, LRU eviction
   - Learn how cache prevents failures

2. **In the code:**
   - `backend/src/main/java/com/transport/tracker/cache/InMemoryCache.java`
   - `backend/src/main/java/com/transport/tracker/cache/CacheService.java`

3. **In the terminal (backend must be running):**
   ```bash
   curl http://localhost:8080/api/v1/cache/stats
   ```
   See cache hit rate and statistics

### Phase 2F: API Client Integration (20 min)
1. **Read:** [docs/explanation-backend.md](./explanation-backend.md) — Section 6: "API Clients"
2. **In the code:**
   - `backend/src/main/java/com/transport/tracker/client/MtaApiClient.java`
   - `backend/src/main/java/com/transport/tracker/client/TransitLandApiClient.java`
   - `backend/src/main/java/com/transport/tracker/client/SeptaApiClient.java`
   - `backend/src/main/java/com/transport/tracker/client/TflApiClient.java`

3. **Understand:**
   - How each implements `TransitApiClient` interface
   - How `selectClient()` chooses the best provider
   - How errors are handled

### Phase 2G: Design Patterns (15 min)
1. **Read:** [docs/ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md) — Section 2
   - Strategy Pattern (API clients)
   - Chain of Responsibility (degradation)
   - Adapter Pattern (data format conversion)

2. **Why it matters:** Understand how flexibility is built in

### Phase 2H: Summary
**You now understand:**
- ✅ All REST endpoints
- ✅ How data flows from API to user
- ✅ How the cache works
- ✅ How failures are handled
- ✅ Design patterns used

---

## Path 3: FRONTEND DEVELOPER PATH (2 hours)

**Goal:** Understand the React components, hooks, styling.

### Phase 3A: Setup (15 min)
1. **Complete FAST TRACK Path 1A-1B above**
2. **Verify Frontend is Running:**
   ```
   Open http://localhost:3000
   ```

### Phase 3B: Project Structure (15 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 2
2. **In the code:**
   ```
   frontend/src/
   ├── App.jsx               ← Main component
   ├── App.css               ← Global styling
   ├── index.css             ← CSS variables (theming)
   ├── components/           ← 7 component folders
   ├── hooks/                ← Custom hooks
   └── services/
       └── apiService.js     ← Backend communication
   ```

### Phase 3C: Main App Component (20 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 5: "App.jsx"
2. **In the code:**
   - Open `frontend/src/App.jsx`
   - Understand the component tree
   - See how state is managed

### Phase 3D: Each Component (40 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 6: "Components Explained"
2. **Study each component in order:**
   - **RouteSearch** — Input component for city/route selection
   - **AlertBanner** — Alert notifications display
   - **ArrivalBoard** — Arrival predictions table
   - **VehicleMap** — Schematic vehicle positions
   - **CrowdingIndicator** — Capacity visual
   - **RoutePlanner** — Journey planning (optional component)
   - **OfflineToggle** — Offline mode toggle

3. **For each component:**
   - Read the explanation in the doc
   - Open the `.jsx` file
   - Understand props and state
   - See how it renders

### Phase 3E: Custom Hooks (15 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 7
2. **In the code:**
   - `frontend/src/hooks/useTransport.js` — Data fetching
   - `frontend/src/hooks/useRoutePlanner.js` (optional) — Route planning

3. **Understand:**
   - How they communicate with backend
   - How they handle loading/error states

### Phase 3F: API Service (15 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 8
2. **In the code:**
   - `frontend/src/services/apiService.js`
   - See how it wraps `fetch()` calls
   - Understand error handling

### Phase 3G: Styling & Theming (15 min)
1. **Read:** [docs/explanation-frontend.md](./explanation-frontend.md) — Section 9
2. **In the code:**
   - `frontend/src/index.css` — CSS variables
   - One component's `.css` file (e.g., `App.css`)
   - See how theming works

### Phase 3H: Summary
**You now understand:**
- ✅ Component hierarchy
- ✅ How each component works
- ✅ Data flow from API to UI
- ✅ Custom hooks pattern
- ✅ Styling and theming

---

## Path 4: ARCHITECT / TECH LEAD PATH (3 hours)

**Goal:** Understand system design, trade-offs, patterns, and business logic.

### Phase 4A: Setup & Overview (15 min)
1. **Complete FAST TRACK Path 1A-1B above**
2. **Read:** [docs/COMPLETE_PROJECT_DOCUMENTATION.md](./COMPLETE_PROJECT_DOCUMENTATION.md) — Sections 1-2
   - Executive summary
   - Technology stack with rationale

### Phase 4B: Architecture Overview (30 min)
1. **Read:** [docs/ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md)
   - Section 1: System architecture diagram
   - Section 3: Resilience strategy (5-stage chain)
   - Section 4: Data flow diagram

2. **Visualize:**
   - How requests flow end-to-end
   - Where caching happens
   - Where failures are handled

### Phase 4C: Design Patterns Deep Dive (30 min)
1. **Read:** [docs/ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md) — Section 2
   - Strategy Pattern (API clients)
   - Chain of Responsibility (degradation)
   - Adapter Pattern (data format conversion)
   - Observer Pattern (auto-refresh)
   - Repository Pattern (cache)
   - Template Method Pattern (error handling)

2. **In the code:** Find examples of each pattern

### Phase 4D: Why Option A Was Chosen (25 min)
1. **Read:** [docs/OPTION_A_SELECTION_RATIONALE.md](./OPTION_A_SELECTION_RATIONALE.md)
   - Risk analysis
   - Why resilience matters most
   - Business impact assessment
   - Future enhancements roadmap

2. **Understand:**
   - Trade-offs between options
   - Why 99.5% availability is critical
   - How degradation prevents user-visible failures

### Phase 4E: Implementation Status (15 min)
1. **Read:** [docs/IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md)
   - 8/8 endpoints complete
   - 7/7 components complete
   - 4/4 API integrations complete

2. **Verify in code:**
   - Spot-check endpoint implementations
   - Note any TODOs or incomplete features

### Phase 4F: Business Logic End-to-End (20 min)
1. **Read:** [docs/explanation-business-logic.md](./explanation-business-logic.md)
   - Sections 3-7
   - Understand degradation chain
   - Understand alert evaluation
   - Understand route planning

2. **In the code:**
   - `TransportService.getTransportData()` — Core degradation logic
   - `AlertService.evaluateCondition()` — Rule evaluation
   - `RoutePlannerService` — Journey planning

### Phase 4G: Scalability & Future (15 min)
1. **Read:** [docs/OPTION_A_SELECTION_RATIONALE.md](./OPTION_A_SELECTION_RATIONALE.md) — "Future Enhancements"
2. **Consider:**
   - Where to add metrics/observability
   - How to scale beyond single instance
   - What's the next priority feature

### Phase 4H: Summary
**You now understand:**
- ✅ System architecture and design
- ✅ All 6 design patterns used
- ✅ Why Option A was chosen
- ✅ Complete business logic flow
- ✅ Implementation status
- ✅ Future enhancement roadmap

---

## Path 5: DEEP DIVE - Complete Expert Understanding (5 hours)

**Goal:** Master the entire codebase from every angle.

### Phase 5A: All Previous Paths (3 hours)
1. **Complete Paths 2, 3, and 4 above**
   - BACKEND PATH (2h)
   - FRONTEND PATH (2h)
   - ARCHITECT PATH (3h)
   
   (This will take 7 hours total, so this phase is actually longer, but recommended)

### Phase 5B: Low-Level Design (30 min)
1. **Read:** [docs/LLD.md](./LLD.md)
   - Class diagrams
   - Method signatures
   - Data structure details

### Phase 5C: Sequence Diagrams (20 min)
1. **Read:** [docs/sequence-diagram.md](./sequence-diagram.md)
   - 6 different request flows
   - Visualize interactions
   - Understand timing

2. **Trace:**
   - Normal flow (cache miss → API call)
   - Failure flow (API down → stale cache)
   - Offline flow (mock data)

### Phase 5D: Test Strategy (20 min)
1. **Read:** [docs/ARCHITECTURE_AND_PATTERNS.md](./ARCHITECTURE_AND_PATTERNS.md) — Section 8
2. **In the code:**
   ```
   backend/src/test/java/com/transport/tracker/
   ├── cache/
   ├── service/
   ├── client/
   └── controller/
   ```
   - Look at test structure
   - Understand mocking strategy
   - See integration test setup

### Phase 5E: Security Implementation (20 min)
1. **Read:** [docs/explanation-backend.md](./explanation-backend.md) — Section 12
2. **In the code:**
   - `backend/src/main/java/com/transport/tracker/config/SecurityConfig.java`
   - Understand CORS configuration
   - See header security policies

### Phase 5F: Deployment & DevOps (20 min)
1. **Read:** [docs/explanation-backend.md](./explanation-backend.md) — Sections 13-14
   - Docker configuration
   - Health checks
   - Deployment process

2. **In the code:**
   - `backend/Dockerfile`
   - `frontend/Dockerfile`
   - `docker-compose.yml`
   - Health check logic

### Phase 5G: Code Review Readiness (15 min)
1. **Run:** `git log --oneline` (if in git)
   - See commit history
   - Understand evolution of code

2. **Check:** Code quality aspects
   - Error handling coverage
   - Test coverage
   - Documentation completeness

### Phase 5H: Summary
**You are now an expert who:**
- ✅ Understands every line of code
- ✅ Can explain design decisions
- ✅ Can propose improvements
- ✅ Can mentor other developers
- ✅ Can review code confidently

---

## Study by Code Execution Flow

For those who prefer to understand code by following execution, here's the **request flow path**:

### 1. Frontend Initiates Request
**File:** `frontend/src/hooks/useTransport.js`
```
User clicks "Search" 
  → useTransport hook called
  → Calls apiService.getTransport()
```

**Study Files:**
- `frontend/src/hooks/useTransport.js` — Request initiation
- `frontend/src/services/apiService.js` — HTTP call to backend

### 2. Backend Receives Request
**File:** `backend/src/main/java/com/transport/tracker/controller/TransportController.java`
```
GET /api/v1/transport?city=nyc&routeId=A
  → TransportController.getTransport() called
  → Calls TransportService.getTransportData()
```

**Study Files:**
- `TransportController.java` — Route handler
- Entry point to service logic

### 3. Service Logic Begins
**File:** `backend/src/main/java/com/transport/tracker/service/TransportService.java`
```
Stage 1: Check if offline mode
Stage 2: Check cache (fresh)
Stage 3: Try live API
  → Calls selectClient() to choose provider
  → Calls client.fetchXxx() methods
Stage 4: On failure, try stale cache
Stage 5: On all failures, return mock data
```

**Study Files:**
- `TransportService.java` — All stages
- `CacheService.java` — Cache interactions
- `MockDataService.java` — Fallback data

### 4. API Client Selected
**File:** `backend/src/main/java/com/transport/tracker/service/TransportService.java:selectClient()`
```
For NYC → Try MtaApiClient → TransitLandApiClient
For London → Try TflApiClient → TransitLandApiClient
...
```

**Study Files:**
- `MtaApiClient.java` — NYC provider
- `TransitLandApiClient.java` — Fallback provider
- `SeptaApiClient.java` — Philadelphia provider
- `TflApiClient.java` — London provider

### 5. Data Transformation
**File:** `backend/src/main/java/com/transport/tracker/service/TransportService.java`
```
Raw API response (different format per provider)
  → Transformed to VehicleLocation/ArrivalPrediction
  → Add metadata (dataSource, cacheAge, etc)
  → Return as TransportData JSON
```

**Study Files:**
- `TransportData.java`, `VehicleLocation.java` — Data models
- Each client's `fetch*()` methods — Transformation logic

### 6. Alerts Evaluation
**File:** `backend/src/main/java/com/transport/tracker/service/AlertService.java`
```
For each alert in data:
  - evaluateCondition() checks: DELAY, DISRUPTION, CROWDING, WEATHER
  - Returns matching alerts
```

**Study Files:**
- `AlertService.java` — Condition evaluation
- `ServiceAlert.java` — Alert data model

### 7. Response Sent to Frontend
**File:** `backend/src/main/java/com/transport/tracker/controller/TransportController.java`
```
TransportData → JSON response
  → HTTP 200 with body
  → Received by apiService.getTransport()
```

### 8. Frontend Displays
**File:** `frontend/src/App.jsx` and components
```
Response received → State updated
  → Re-render components
    → RouteSearch
    → AlertBanner (show alerts)
    → VehicleMap (show positions)
    → ArrivalBoard (show arrivals)
    → CrowdingIndicator (show capacity)
```

**Study Files:**
- `App.jsx` — State management
- `components/*` — Rendering logic

---

## Code Reading Order (By Importance)

Read in this order to build understanding progressively:

### Level 1: Core Business Logic (Essential, Start Here)
1. `backend/src/main/java/com/transport/tracker/service/TransportService.java`
   - Main degradation logic
   - Cache integration
   - Client selection

2. `backend/src/main/java/com/transport/tracker/cache/InMemoryCache.java`
   - Understand TTL and stale-TTL
   - Thread safety
   - LRU eviction

3. `frontend/src/App.jsx`
   - Component tree
   - State management
   - Hook usage

### Level 2: API & Controllers (Important)
4. `backend/src/main/java/com/transport/tracker/controller/TransportController.java`
   - REST endpoints
   - Request handling

5. `backend/src/main/java/com/transport/tracker/client/TransitApiClient.java` (interface)
   - Contract for all clients
   - Methods each provider implements

6. `frontend/src/services/apiService.js`
   - Backend communication
   - HTTP configuration

### Level 3: Data Models (Important)
7. `backend/src/main/java/com/transport/tracker/model/` — All model files
   - TransportData
   - VehicleLocation
   - ArrivalPrediction
   - ServiceAlert

8. `frontend/src/components/` — All component files
   - Understand component structure
   - Props and state

### Level 4: Feature Implementations (Understand details)
9. API Client implementations:
   - `MtaApiClient.java`
   - `TransitLandApiClient.java`
   - `SeptaApiClient.java`
   - `TflApiClient.java`

10. `backend/src/main/java/com/transport/tracker/service/AlertService.java`
    - Condition evaluation logic

11. `backend/src/main/java/com/transport/tracker/service/RoutePlannerService.java` (optional)
    - Journey planning logic

### Level 5: Configuration & Utilities (Reference)
12. Configuration classes:
    - `AppConfig.java` — Spring bean setup
    - `SecurityConfig.java` — CORS and headers

13. Exception handling:
    - `GlobalExceptionHandler.java`
    - `TransitApiException.java`

### Level 6: Testing (Learn testing patterns)
14. Test files in `backend/src/test/`
    - Unit tests
    - Integration tests
    - Mocking examples

---

## Quick Reference: Key Concepts

### 5-Stage Degradation Chain
```
Stage 1: Offline mode? → Return MOCK
Stage 2: Cache hit? → Return CACHE
Stage 3: API up? → Fetch LIVE, cache it
Stage 4: API down? → Return STALE_CACHE
Stage 5: No stale? → Return MOCK
```

### Data Source Values
- **LIVE** — Fresh data from API
- **CACHE** — Data within TTL (fresh cache)
- **STALE_CACHE** — Data within stale-TTL (expired but useful)
- **MOCK** — Synthetic test data

### Provider Selection Order
- **NYC** → MTA, then TransitLand, then TfL, then SEPTA
- **London** → TfL, then TransitLand, then MTA, then SEPTA
- **Philadelphia** → SEPTA, then TransitLand, then TfL, then MTA
- **Other** → TransitLand, then TfL, then MTA, then SEPTA

### Alert Types
- **DELAY** — Vehicle delayed > 15 minutes
- **DISRUPTION** — Service suspended/modified
- **CROWDING** — Vehicle at full capacity
- **WEATHER** — Weather-related alert

---

## Study Checklist

Track your progress with this checklist:

### Documentation Reading
- [ ] GETTING_STARTED.md
- [ ] API.md
- [ ] explanation-backend.md
- [ ] explanation-frontend.md
- [ ] explanation-business-logic.md
- [ ] ARCHITECTURE_AND_PATTERNS.md
- [ ] OPTION_A_SELECTION_RATIONALE.md
- [ ] sequence-diagram.md
- [ ] LLD.md

### Code Reading (Level 1)
- [ ] TransportService.java
- [ ] InMemoryCache.java
- [ ] App.jsx

### Code Reading (Level 2)
- [ ] TransportController.java
- [ ] TransitApiClient.java (interface)
- [ ] apiService.js

### Code Reading (Level 3)
- [ ] All model files in `backend/src/main/java/com/transport/tracker/model/`
- [ ] All component files in `frontend/src/components/`

### Code Reading (Level 4)
- [ ] All API client implementations
- [ ] AlertService.java
- [ ] RoutePlannerService.java

### Practical Verification
- [ ] Run backend successfully
- [ ] Run frontend successfully
- [ ] Test with NYC route
- [ ] Test offline mode
- [ ] Test cache behavior
- [ ] Check health endpoint
- [ ] View cache statistics

---

## Time Estimates by Path

| Path | Duration | Best For |
|------|----------|----------|
| **FAST TRACK** | 1 hour | Managers, stakeholders, quick overview |
| **BACKEND PATH** | 2 hours | Backend developers |
| **FRONTEND PATH** | 2 hours | Frontend developers |
| **ARCHITECT PATH** | 3 hours | Tech leads, architects, seniors |
| **DEEP DIVE** | 5+ hours | Complete mastery, code reviewers |

---

## Next Steps After Learning

Once you've completed your learning path:

1. **Run the application** — Hands-on experience
2. **Make a small change** — Modify a component or service
3. **Test the change** — Verify it works
4. **Review your changes** — Understand the impact
5. **Read related code** — Understand dependencies
6. **Write tests** — Solidify understanding
7. **Document insights** — Share knowledge with team

---

## Getting Help

If you're stuck:

1. **Check the FAQ section** in [GETTING_STARTED.md](./GETTING_STARTED.md)
2. **Review the sequence diagrams** in [sequence-diagram.md](./sequence-diagram.md)
3. **Look at tests** in `backend/src/test/` — Tests document expected behavior
4. **Check logs** in the running application — Logs show execution flow
5. **Read the code comments** — Developers left explanations

---

## Final Wisdom

> "Reading code is harder than writing code. Don't try to understand everything at once. Follow the flow. Ask 'what happens when...' and trace the code."

Start with the FAST TRACK path to see the application working, then choose your role-based path to understand the details. The journey from "what is this?" to "I understand every line" typically takes a day of focused learning.

Good luck! 🚀