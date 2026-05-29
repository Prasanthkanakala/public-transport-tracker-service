# Local Test Execution Workflow

## Quick Answer
✅ **YES** - `gradle test` will execute BDD tests automatically
❌ **NO** - You cannot run `gradle bootRun` and `gradle test` in the same terminal

## Proper Local Setup

### 📋 Prerequisites Check
- [x] Backend Java code exists
- [x] Test runner classes created (6 files)
- [x] Feature files created (9 files)
- [x] Karate dependencies added to build.gradle
- [x] Configuration file (karate-config.js) created

### 🚀 Step-by-Step Local Execution

#### **Step 1: Terminal 1 - Start Backend (Keep this running)**
```bash
cd c:\Users\kanprasa1\IdeaProjects\public transport tracker service\backend
./gradlew bootRun
```

**Wait for output like:**
```
2024-05-27 10:30:00 - Started TransportTrackerApplication in 8.234 seconds
```

**LEAVE THIS TERMINAL RUNNING** - Don't close it!

---

#### **Step 2: Terminal 2 - Run BDD Tests (New terminal)**
```bash
cd c:\Users\kanprasa1\IdeaProjects\public transport tracker service\backend
./gradlew test
```

**What happens:**
1. Gradle compiles all test classes
2. Discovers Karate test runners (6 classes)
3. Loads feature files (9 files)
4. Executes all 95+ BDD scenarios
5. Generates test report

**Expected Output:**
```
> Task :test
BUILD SUCCESSFUL
95 tests passed in 5.23 minutes
```

---

### 🎯 Common Test Commands

**Run All Tests:**
```bash
./gradlew test
```

**Run Only Smoke Tests (Fast):**
```bash
./gradlew test -Dkarate.tags="@smoke"
```

**Run Only Regression Tests:**
```bash
./gradlew test -Dkarate.tags="@regression"
```

**Run Specific Feature:**
```bash
./gradlew test --tests TransportApiTest
./gradlew test --tests RoutingApiTest
./gradlew test --tests IntegrationTest
```

**Run Error Handling Tests:**
```bash
./gradlew test --tests ErrorHandlingTest
```

---

## ⚠️ Important Notes

### Why You Need Two Terminals

Gradle runs blocking commands. When `bootRun` is active, it holds the terminal and listens for HTTP requests.

```
Terminal 1 (Backend):
./gradlew bootRun  
    ↓
Starts server on :8080
    ↓
Blocks waiting for HTTP requests
    ↓
CANNOT run other commands here!
```

```
Terminal 2 (Tests):
./gradlew test
    ↓
Compiles tests
    ↓
Sends HTTP requests to localhost:8080 (from Terminal 1)
    ↓
Gets responses from Terminal 1
    ↓
Validates responses
    ↓
Reports results
```

### What NOT to Do

❌ **DON'T** close Terminal 1 while running tests - server stops!
```bash
# Terminal 1
./gradlew bootRun
# ← If you close this, Terminal 2 tests will fail!
```

❌ **DON'T** try to run both in same terminal
```bash
./gradlew bootRun          # Blocks here
./gradlew test             # Never reaches this line!
```

❌ **DON'T** run other gradle commands in Terminal 1
```bash
./gradlew bootRun
# ↓ Terminal 1 is blocked, can't do anything else
```

---

## ✅ Correct Workflow

### Setup (First time only)
```bash
# Terminal 1 - Navigate to backend
cd backend
./gradlew clean build

# If build successful, ready to run tests
```

### Every Test Run
```bash
# Terminal 1 - Start backend (KEEP RUNNING)
./gradlew bootRun
# Wait for: "Started TransportTrackerApplication in X seconds"

# Terminal 2 - Run tests (while Terminal 1 is running)
./gradlew test
```

### View Results
```bash
# After tests complete, view report
open build/reports/tests/test/index.html
```

---

## 📊 Test Execution Timeline

```
Time │ Terminal 1           │ Terminal 2
─────┼──────────────────────┼────────────────────────
0s   │ ./gradlew bootRun    │
     │ Starting...          │
2s   │ Server started :8080 │
3s   │ Listening...         │ ./gradlew test
4s   │ Waiting...           │ Compiling tests...
5s   │ Listening...         │ Loading karate-config
6s   │ Waiting...           │ Executing 95 scenarios
7s   │ Request: GET /api... │
8s   │ Response: 200 OK     │
...  │                      │
250s │ Listening...         │ ✅ All 95 tests passed
     │                      │ Report: build/reports/...
     │ (Keep running)       │
```

---

## 🔧 Troubleshooting

### Tests Fail: "Connection refused"
**Problem:** Backend is not running
```bash
# Terminal 1 should show:
# "Started TransportTrackerApplication in X seconds"
```

**Fix:**
1. Close Terminal 2
2. Check Terminal 1 is still running backend
3. Look for error messages in Terminal 1
4. If Terminal 1 crashed, restart it: `./gradlew bootRun`
5. Try tests again in Terminal 2

### Backend Won't Start

Check if port 8080 is in use:
```bash
# On Windows, find what's using port 8080
netstat -ano | findstr :8080

# Kill the process if needed
taskkill /PID <PID> /F
```

### Tests Hang or Timeout

**Problem:** Backend crashed or tests waiting for response

**Fix:**
1. Check Terminal 1 - is backend still running?
2. Look for errors in Terminal 1
3. If backend crashed:
   - Restart Terminal 1: `./gradlew bootRun`
   - Re-run Terminal 2: `./gradlew test`

### Port Already in Use

```bash
# Terminal 1 fails to start with:
# "Address already in use: bind"

# Find and kill process using port 8080:
# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# macOS/Linux:
lsof -i :8080
kill -9 <PID>
```

---

## ✨ Advanced Usage

### Run Tests with Custom Config
```bash
# Set different base URL
./gradlew test -Dkarate.env=staging

# Enable debug logging
./gradlew test -Dkarate.loglevel=debug

# Run with specific threads
./gradlew test -Dkarate.threads=2
```

### Generate Performance Report
```bash
./gradlew test -Dkarate.tags="@performance"
# Then check: build/reports/tests/test/index.html
```

### Run Tests Only (Skip Build)
```bash
./gradlew test --no-build-cache
```

---

## 📝 Checklist Before Testing

- [ ] JDK 17+ installed
- [ ] Backend code compiles: `./gradlew clean build`
- [ ] All 6 test runner Java files present (bdd folder)
- [ ] All 9 feature files present (resources/karate folder)
- [ ] Karate dependencies in build.gradle
- [ ] karate-config.js in resources/karate folder

---

## 🎯 Summary

| Command | Terminal | Effect |
|---------|----------|--------|
| `./gradlew bootRun` | 1 | Starts backend, **BLOCKS** |
| `./gradlew test` | 2 | Runs tests, **MUST WAIT** for Terminal 1 |
| `./gradlew test -Dkarate.tags="@smoke"` | 2 | Quick smoke tests only |
| `Ctrl+C` | 1 | Stops backend |
| `Ctrl+C` | 2 | Stops tests |

**Remember:** Terminal 1 must keep running while Terminal 2 runs tests!

---

**Last Updated:** May 27, 2024
