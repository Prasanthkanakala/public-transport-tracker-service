# 🚀 BDD Test Execution - Visual Guide

## Quick Answer
```
❓ Will BDD tests run with gradle test?
✅ YES - Automatically! All 95+ scenarios execute.

❓ Can I run gradle bootRun and gradle test together?
❌ NO - Need 2 separate terminals
```

---

## The Correct Setup

```
┌─────────────────────────────────────────────────────────────────┐
│                    YOUR COMPUTER                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌────────────────────┐          ┌────────────────────────────┐ │
│  │   TERMINAL 1       │          │     TERMINAL 2             │ │
│  │   (Backend)        │          │     (Tests)                │ │
│  ├────────────────────┤          ├────────────────────────────┤ │
│  │                    │          │                            │ │
│  │ $ cd backend       │          │ $ cd backend               │ │
│  │ $ ./gradlew        │          │ $ ./gradlew test           │ │
│  │   bootRun          │          │                            │ │
│  │                    │          │ Loading tests...           │ │
│  │ Starting...        │          │ ────────────────           │ │
│  │ ➜ Listening on     │          │ Executing 95+ scenarios    │ │
│  │   :8080  ✓         │  ◄───────┤ • transport.feature: 10 ✓ │ │
│  │                    │          │ • arrivals.feature: 10 ✓  │ │
│  │ [KEEP RUNNING]     │          │ • vehicles.feature: 11 ✓  │ │
│  │                    │          │ • alerts.feature: 11 ✓    │ │
│  │                    │          │ • routing.feature: 14 ✓   │ │
│  │                    │          │ • crowding.feature: 11 ✓  │ │
│  │                    │          │ • cache.feature: 12 ✓     │ │
│  │                    │          │ • error-scenarios: 20 ✓   │ │
│  │                    │          │ • integration.feature: 15 ✓│ │
│  │                    │          │                            │ │
│  │                    │          │ ✅ ALL PASSED              │ │
│  │ (Waiting for       │          │ 95 tests passed in 5m      │ │
│  │  HTTP requests)    │          │                            │ │
│  │                    │          │ View report:               │ │
│  └────────────────────┘          │ build/reports/tests/       │ │
│                                   │ test/index.html            │ │
│                                   └────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Timeline

```
Step 1: Start Backend
═════════════════════════════════════════════════════════════════

Terminal 1> ./gradlew bootRun
   ↓
   Downloading dependencies...
   Compiling...
   Starting TransportTrackerApplication...
   ✓ Server started on :8080 
   ✓ Ready for requests
   
   ⏸️  STOPS HERE - WAITS FOR HTTP REQUESTS - DO NOT CLOSE!


Step 2: Run Tests (While Step 1 is still running)
═════════════════════════════════════════════════════════════════

Terminal 2> ./gradlew test
   ↓
   Discovering test classes...
   Found: KarateTestSuite.java ✓
   Found: TransportApiTest.java ✓
   Found: RoutingApiTest.java ✓
   Found: CacheApiTest.java ✓
   Found: ErrorHandlingTest.java ✓
   Found: IntegrationTest.java ✓
   ↓
   Loading feature files...
   Found: transport.feature (10 scenarios)
   Found: arrivals.feature (10 scenarios)
   Found: vehicles.feature (11 scenarios)
   Found: alerts.feature (11 scenarios)
   Found: routing.feature (14 scenarios)
   Found: crowding.feature (11 scenarios)
   Found: cache.feature (12 scenarios)
   Found: error-scenarios.feature (20 scenarios)
   Found: integration.feature (15 scenarios)
   ↓
   Running 95+ scenarios...
   ✓ transport:smoke - Get all transport data
   ✓ transport:positive - With route filter
   ✓ arrivals:smoke - Get predictions
   ✓ vehicles:positive - GPS validation
   ✓ alerts:negative - Error handling
   ... (90 more tests)
   ↓
   ✅ ALL TESTS PASSED
   95 tests in 5 minutes 23 seconds


Step 3: View Results
═════════════════════════════════════════════════════════════════

Terminal 2> open build/reports/tests/test/index.html
   
   OR in Windows:
   start build/reports/tests/test/index.html
   ↓
   HTML report opens with:
   - Test summary
   - Pass/fail breakdown
   - Execution times
   - Failure details (if any)
```

---

## What Happens Under the Hood

```
TERMINAL 1: Backend              TERMINAL 2: Tests          HTTP
─────────────────────────────────────────────────────────
Listening on :8080              Running tests
                                  │
                                  ├──→ GET /api/v1/transport
Backend receives      ◄───────────┤
Processes request                 │
Returns JSON ────────────────────→ │
                                  ├──→ Validate response ✓
                                  │
                                  ├──→ GET /api/v1/arrivals
Backend receives      ◄───────────┤
Processes request                 │
Returns JSON ────────────────────→ │
                                  ├──→ Validate response ✓
                                  │
                                  └──→ Continue...
```

---

## Common Mistakes ❌

### Mistake 1: Closing Backend While Tests Run
```
Terminal 1
─────────────────────
./gradlew bootRun
✓ Server started
↓
[User closes terminal]
✗ Server stopped!

Terminal 2
─────────────────────
./gradlew test
Running tests...
✗ Connection refused!
✗ Tests fail!
```

### Mistake 2: Running Both in Same Terminal
```
Terminal
─────────────────────
./gradlew bootRun      # Blocks here
./gradlew test         # Never runs!
```

### Mistake 3: Starting Tests Before Backend Ready
```
Terminal 1              Terminal 2
───────────────────     ─────────────────────
./gradlew bootRun       ./gradlew test
Starting...             Running tests...
                        ✗ Connection refused!
Starting...
Still starting...       ✗ Timeout!
```

---

## The Right Way ✅

```
Terminal 1              Terminal 2
───────────────────     ─────────────────────
$ cd backend            $ cd backend
$ ./gradlew bootRun     

Starting...             
✓ Server ready          
(KEEP RUNNING)          $ ./gradlew test
                        
                        Running 95 scenarios...
                        ✅ PASSED
```

---

## Command Reference

### Start Backend (Terminal 1)
```bash
cd backend
./gradlew bootRun
# Keep this running!
```

### Run All Tests (Terminal 2)
```bash
cd backend
./gradlew test
```

### Run Smoke Tests Only (Terminal 2)
```bash
cd backend
./gradlew test -Dkarate.tags="@smoke"
# Takes ~1.5 minutes instead of 5
```

### Run Specific Test Suite (Terminal 2)
```bash
cd backend
./gradlew test --tests TransportApiTest      # API tests
./gradlew test --tests RoutingApiTest        # Routing tests
./gradlew test --tests CacheApiTest          # Cache tests
./gradlew test --tests ErrorHandlingTest     # Error tests
./gradlew test --tests IntegrationTest       # Integration tests
```

### View Report (Terminal 2, after tests)
```bash
# Windows
start build/reports/tests/test/index.html

# macOS
open build/reports/tests/test/index.html

# Linux
xdg-open build/reports/tests/test/index.html
```

---

## Checklist ✓

Before you start:
- [ ] Backend code exists
- [ ] Test files exist (6 Java files)
- [ ] Feature files exist (9 .feature files)
- [ ] build.gradle has Karate dependencies
- [ ] karate-config.js exists
- [ ] Have 2 terminal windows open

Before running tests:
- [ ] Terminal 1: Backend is running
- [ ] Terminal 1: "Server started" message visible
- [ ] Terminal 1: No error messages
- [ ] Terminal 2: Ready to run tests

---

## Expected Execution Time

```
./gradlew test -Dkarate.tags="@smoke"
├─ Transport API tests: 15 sec
├─ Arrivals API tests: 15 sec
├─ Vehicles API tests: 18 sec
├─ Alerts API tests: 18 sec
└─ Total: ~1.5 minutes ⏱️

./gradlew test -Dkarate.tags="@regression"
├─ All 95+ scenarios
└─ Total: ~5-6 minutes ⏱️
```

---

## 🎯 Summary

| Question | Answer |
|----------|--------|
| Will `gradle test` run BDD? | ✅ YES, all 95+ scenarios |
| Can I run both in one terminal? | ❌ NO, need 2 terminals |
| Do I close backend while testing? | ❌ NO, keep it running |
| Where's the test report? | `build/reports/tests/test/index.html` |
| How long do tests take? | 5-6 minutes (or 1.5 min for @smoke) |

---

**Ready to test?** Follow the visual diagram above! 🚀
