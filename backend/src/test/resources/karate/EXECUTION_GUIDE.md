# Test Execution Examples

## Maven Configuration (Alternative to Gradle)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-junit5</artifactId>
    <version>1.4.1</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-core</artifactId>
    <version>1.4.1</version>
    <scope>test</scope>
</dependency>
```

## Gradle Command Examples

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
# Transport API tests
./gradlew test --tests TransportApiTest

# Just arrivals
./gradlew test --tests TransportApiTest --tests "*testArrivals*"

# Error handling
./gradlew test --tests ErrorHandlingTest

# Integration tests
./gradlew test --tests IntegrationTest
```

### Run by Feature Tag
```bash
# All smoke tests (quick validation)
./gradlew test -Dkarate.tags="@smoke"

# Full regression suite
./gradlew test -Dkarate.tags="@regression"

# Only positive scenarios
./gradlew test -Dkarate.tags="@positive"

# Only negative/error scenarios
./gradlew test -Dkarate.tags="@negative"

# Exclude integration tests
./gradlew test -Dkarate.tags="not @integration"

# Combine tags (AND)
./gradlew test -Dkarate.tags="@regression and @performance"

# Multiple tags (OR)
./gradlew test -Dkarate.tags="@smoke or @performance"
```

### Run Specific Feature File
```bash
# Transport feature
./gradlew test -Dkarate.features="classpath:karate/transport.feature"

# Multiple features
./gradlew test -Dkarate.features="classpath:karate/arrivals.feature,classpath:karate/vehicles.feature"
```

### Run with Custom Configuration
```bash
# Set base URL
./gradlew test -Dkarate.env=prod

# Set log level (DEBUG, INFO, WARN)
./gradlew test -Dkarate.loglevel=debug

# Parallel execution (threads)
./gradlew test -Dkarate.threads=4

# Combination
./gradlew test -Dkarate.tags="@regression" -Dkarate.env=staging -Dkarate.threads=2
```

## Maven Command Examples

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TransportApiTest

# Run with tag
mvn test -Dkarate.tags="@smoke"

# Run specific feature
mvn test -Dkarate.features="classpath:karate/transport.feature"
```

## Continuous Integration Examples

### GitHub Actions
```yaml
name: BDD Tests
on: [push, pull_request]

jobs:
  bdd-tests:
    runs-on: ubuntu-latest
    services:
      backend:
        image: transport-api:latest
        ports:
          - 8080:8080
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run Smoke Tests
        run: ./gradlew test -Dkarate.tags="@smoke"
      
      - name: Run Regression Tests
        run: ./gradlew test -Dkarate.tags="@regression"
      
      - name: Publish Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: build/reports/tests/test/
```

### GitLab CI
```yaml
test:
  image: gradle:7.6-jdk17
  stages:
    - test
  script:
    - ./gradlew test -Dkarate.tags="@regression" --no-daemon
  artifacts:
    reports:
      junit: build/test-results/test/TEST-*.xml
    paths:
      - build/reports/tests/test/
    expire_in: 30 days
```

### Jenkins
```groovy
pipeline {
    agent any
    
    stages {
        stage('Smoke Tests') {
            steps {
                sh './gradlew test -Dkarate.tags="@smoke"'
            }
        }
        
        stage('Regression Tests') {
            steps {
                sh './gradlew test -Dkarate.tags="@regression"'
            }
        }
        
        stage('Performance Tests') {
            steps {
                sh './gradlew test -Dkarate.tags="@performance"'
            }
        }
    }
    
    post {
        always {
            junit 'build/test-results/test/TEST-*.xml'
            publishHTML([
                reportDir: 'build/reports/tests/test/',
                reportFiles: 'index.html',
                reportName: 'Karate Test Report'
            ])
        }
    }
}
```

## Test Scenarios with Examples

### Example 1: Smoke Test Run (2 minutes)
```bash
./gradlew test -Dkarate.tags="@smoke"
```
**What it does:**
- Runs 10 quick validation tests
- Validates basic API functionality
- Good for rapid feedback in CI/CD

**Output:**
```
10 tests passed in 1.23 minutes
```

### Example 2: Full Regression Run (5 minutes)
```bash
./gradlew test -Dkarate.tags="@regression"
```
**What it does:**
- Runs all 95+ test scenarios
- Comprehensive validation of all endpoints
- Includes error handling and integration tests

**Output:**
```
95 tests passed in 4.56 minutes
```

### Example 3: API Integration Workflow (1 minute)
```bash
./gradlew test --tests IntegrationTest -Dkarate.tags="@integration"
```
**What it does:**
- Tests complete user journeys
- Validates cross-endpoint consistency
- End-to-end scenario validation

**Output:**
```
15 integration tests passed in 1.12 minutes
```

### Example 4: Error Handling Validation (2 minutes)
```bash
./gradlew test --tests ErrorHandlingTest -Dkarate.tags="@negative"
```
**What it does:**
- Tests invalid inputs
- Validates error responses
- Security scenario validation

**Output:**
```
25 error scenario tests passed in 1.89 minutes
```

### Example 5: Performance Baseline (3 minutes)
```bash
./gradlew test -Dkarate.tags="@performance"
```
**What it does:**
- Validates response times
- Measures endpoint performance
- Captures performance metrics

**Output:**
```
10 performance tests passed in 2.45 minutes
Performance Report:
- /transport: avg 1.2s, max 1.8s
- /arrivals: avg 0.8s, max 1.2s
- /plan: avg 2.1s, max 3.1s
```

## Debugging & Troubleshooting

### Enable Debug Logging
```bash
./gradlew test -Dkarate.loglevel=debug -Dkarate.tags="@smoke"
```

### Run Single Test Scenario
```bash
./gradlew test -Dkarate.scenarios="Get all transport data for a city"
```

### Run and Generate Report
```bash
./gradlew test -Dkarate.tags="@regression" && open build/reports/tests/test/index.html
```

### View Test Results
```bash
# Linux/Mac
open build/reports/tests/test/index.html

# Windows
start build/reports/tests/test/index.html
```

## Performance Profiling

### Run with Thread Dump
```bash
./gradlew test -Dkarate.threads=1 -Dkarate.tags="@performance"
```

### Measure Test Execution Time
```bash
time ./gradlew test -Dkarate.tags="@regression"
```

## Parallel Execution

### Run with Multiple Threads
```bash
# 2 threads
./gradlew test -Dkarate.threads=2

# 4 threads (2x faster, if 4+ CPU cores)
./gradlew test -Dkarate.threads=4

# Auto-detect optimal threads
./gradlew test -Dkarate.threads=0
```

**Note:** Parallel execution may cause issues with shared state (cache). Use `?offline=true` or clear cache between tests for isolation.

## Test Report Analysis

After running tests, analyze:

1. **Summary Statistics**
   - Total tests run
   - Pass/fail/skip counts
   - Execution time

2. **Failed Tests**
   - Assertion error messages
   - Expected vs actual values
   - Stack traces

3. **Performance Metrics**
   - Response times per endpoint
   - Slowest tests
   - Performance regression detection

4. **Coverage Analysis**
   - Endpoint coverage
   - Tag distribution
   - Scenario coverage

## Best Practices

### 1. Local Development
```bash
# Run smoke tests frequently
./gradlew test -Dkarate.tags="@smoke"

# Run full suite before push
./gradlew test -Dkarate.tags="@regression"
```

### 2. CI/CD Pipeline
```bash
# Quick validation on push
@smoke

# Full validation on PR
@regression

# Performance baseline on release
@regression and @performance
```

### 3. Test Scheduling
```bash
# Hourly: smoke tests
0 * * * * ./gradlew test -Dkarate.tags="@smoke"

# Daily: regression tests
0 2 * * * ./gradlew test -Dkarate.tags="@regression"

# Weekly: full suite with stress tests
0 0 * * 0 ./gradlew test -Dkarate.tags="@regression"
```

---

For more information, see [README.md](README.md) or [QUICK_START.md](QUICK_START.md)
