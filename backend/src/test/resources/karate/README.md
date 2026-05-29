# Karate BDD Test Suite - Transport API

## Overview

This directory contains comprehensive BDD (Behavior-Driven Development) test cases for the Transport API backend, written using **Karate Framework**. Karate is a open-source framework for API automation testing that combines API testing, mocking, performance testing with a simple and intuitive BDD syntax.

## Directory Structure

```
backend/src/test/
├── java/com/transport/tracker/bdd/
│   ├── KarateTestSuite.java           # Main test suite runner
│   ├── TransportApiTest.java          # Transport endpoint tests
│   ├── RoutingApiTest.java            # Routing and crowding tests
│   ├── CacheApiTest.java              # Cache operations tests
│   ├── ErrorHandlingTest.java         # Error scenarios tests
│   └── IntegrationTest.java           # Integration tests
└── resources/karate/
    ├── karate-config.js               # Global configuration
    ├── transport.feature              # Main transport data API
    ├── arrivals.feature               # Arrival predictions API
    ├── vehicles.feature               # Vehicle locations API
    ├── alerts.feature                 # Service alerts API
    ├── routing.feature                # Route planning API
    ├── crowding.feature               # Crowding information API
    ├── cache.feature                  # Cache management API
    ├── error-scenarios.feature        # Error handling tests
    └── integration.feature            # End-to-end scenarios
```

## Feature Files Overview

### 1. **transport.feature** (@transport @regression)
Tests the main Transport API endpoint `/api/v1/transport` that returns complete transport data.

**Test Scenarios:**
- Get all transport data for a city (smoke test)
- Get transport data with route filter
- Get transport data in offline mode
- Validate metadata in responses
- Test without required parameters (negative)
- Performance testing
- HATEOAS links validation

**Key Assertions:**
- Status 200 OK
- Response contains vehicles, arrivals, alerts, crowding, routePlans
- Metadata includes cached status, dataSource, timestamp
- Response time < 5000ms

---

### 2. **arrivals.feature** (@arrivals @regression)
Tests arrival predictions endpoint `/api/v1/transport/arrivals`.

**Test Scenarios:**
- Get arrival predictions for a stop (smoke test)
- Get arrivals with route filter
- Validate arrival prediction structure
- Verify delay calculation
- Check realtime status
- Test missing required parameters (negative)
- Test offline mode
- Performance testing
- Verify caching behavior

**Key Assertions:**
- Status 200 OK
- Response includes scheduledArrival, predictedArrival, delaySeconds
- Status values: ON_TIME, DELAYED, EARLY, CANCELLED, NO_DATA
- Delay calculations are accurate (e.g., DELAYED status when delaySeconds > 900)

---

### 3. **vehicles.feature** (@vehicles @regression)
Tests vehicle locations endpoint `/api/v1/transport/vehicles`.

**Test Scenarios:**
- Get vehicle locations for all routes (smoke test)
- Get vehicle locations for specific route
- Validate vehicle location structure
- Verify vehicle status values
- Check delay information
- Verify occupancy status
- Realtime data accuracy
- Performance testing
- GPS accuracy validation

**Key Assertions:**
- Status 200 OK
- Latitude/Longitude within valid ranges (-90 to 90, -180 to 180)
- Bearing between 0-360 degrees
- Speed >= 0 km/h
- Status values: IN_TRANSIT_TO, STOPPED_AT, INCOMING_AT
- Occupancy status: EMPTY, MANY_SEATS, FEW_SEATS, STANDING_ONLY, CRUSHED, FULL

---

### 4. **alerts.feature** (@alerts @regression)
Tests service alerts endpoint `/api/v1/transport/alerts`.

**Test Scenarios:**
- Get service alerts for a city (smoke test)
- Validate alert structure
- Verify affected routes in alerts
- Check alert time windows
- Filter critical alerts
- Verify disruption alert details
- Test offline mode
- Performance testing
- Weather alerts verification
- Caching behavior

**Key Assertions:**
- Status 200 OK
- Alert types: DELAY, DISRUPTION, WEATHER, CROWDING, PLANNED_WORK, GENERAL_INFO
- Severity levels: LOW, MEDIUM, HIGH, CRITICAL
- Effect values: DETOUR, STOP_MOVED, SERVICE_CHANGE, SUSPENSION, SIGNIFICANT_DELAYS, REDUCED_SERVICE
- Cause values: TECHNICAL_PROBLEM, ACCIDENT, WEATHER, MAINTENANCE, CONSTRUCTION

---

### 5. **routing.feature** (@routing @regression)
Tests route planning endpoint `/api/v1/transport/plan`.

**Test Scenarios:**
- Plan route between two locations (smoke test)
- Validate route plan structure
- Verify route plan legs
- Get multiple route alternatives
- Verify first route is optimal
- Verify minimum transfer time
- Verify confidence score
- Test missing/invalid parameters (negative)
- Test offline mode
- Performance testing

**Key Assertions:**
- Status 200 OK
- Response contains 1-3 route alternatives
- First plan status: OPTIMAL
- Duration increases for alternative routes
- Confidence score: 0.0 to 1.0
- Legs contain mode, route, startTime, endTime, stops

---

### 6. **crowding.feature** (@crowding @regression)
Tests crowding information endpoint `/api/v1/transport/crowding`.

**Test Scenarios:**
- Get crowding information for a route (smoke test)
- Validate crowding data structure
- Verify crowding levels
- Verify GTFS occupancy status
- Validate occupancy percentage calculation
- Route-specific filtering
- Test missing parameters (negative)
- Test offline mode
- Performance testing
- Verify crowding-related alerts

**Key Assertions:**
- Status 200 OK
- Capacity >= currentPassengers >= 0
- Occupancy percentage calculation: (currentPassengers / capacity) * 100
- Level values: LOW, MEDIUM, HIGH, FULL
- GTFS occupancy: EMPTY to FULL (6 levels)

---

### 7. **cache.feature** (@cache @regression)
Tests cache management endpoints `/api/v1/cache`.

**Test Scenarios:**
- Get cache statistics (smoke test)
- Verify cache statistics calculation
- Verify hit count increment
- Clear all cache entries
- Delete specific cache entry
- Verify cache entry deletion
- Performance testing
- Test invalid parameters (negative)
- Verify stale cache hits
- Monitor cache eviction
- Stress testing with 10 requests
- Cache data freshness validation

**Key Assertions:**
- Status 200 OK or 204 No Content (for DELETE)
- Cache stats include: hitCount, missCount, staleHitCount, evictionCount, cacheSize, hitRate
- Hit rate calculation: hitCount / (hitCount + missCount + staleHitCount)
- After cache clear: cacheSize == 0

---

### 8. **error-scenarios.feature** (@error-scenarios @regression)
Tests error handling and validation.

**Test Scenarios:**
- Missing required parameters (e.g., stopId)
- Invalid coordinate formats
- Out of range coordinates
- Invalid boolean parameters
- Error response structure validation
- Invalid city graceful degradation
- SQL injection prevention
- XSS prevention
- Null value handling
- Error recovery
- Timeout handling
- Unsupported content-type
- CORS headers validation
- Duplicate parameters

**Key Assertions:**
- Status 400 for validation errors
- Error responses include: error, message, status, path, timestamp
- Invalid city returns MOCK data gracefully
- Security attempts don't crash system

---

### 9. **integration.feature** (@integration @regression)
Tests end-to-end user journeys and API integration.

**Test Scenarios:**
- Complete user journey: transport data → arrivals → alerts → route planning
- Cache effectiveness across multiple endpoints
- Cross-endpoint data consistency
- Offline mode consistency
- Data freshness tracking
- Error recovery and continued operation
- Graceful degradation
- Multi-city journey planning
- Alert propagation
- Performance under sequential calls
- Data correlation between endpoints
- Cache invalidation after update

**Key Assertions:**
- Status 200 OK for all endpoints
- Data consistency between related endpoints
- Offline mode works uniformly across all endpoints
- System recovers from errors
- Cache improves response times

---

## Running the Tests

### Prerequisites
- JDK 17+
- Maven or Gradle
- Backend API running on `http://localhost:8080`

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Suite
```bash
# Transport API tests
./gradlew test --tests TransportApiTest

# Routing tests
./gradlew test --tests RoutingApiTest

# Cache tests
./gradlew test --tests CacheApiTest

# Error handling tests
./gradlew test --tests ErrorHandlingTest

# Integration tests
./gradlew test --tests IntegrationTest

# Full BDD suite
./gradlew test --tests KarateTestSuite
```

### Run Tests with Tags
```bash
# Only smoke tests
./gradlew test --tests '*Test' -Dkarate.tags="@smoke"

# Regression tests only
./gradlew test --tests '*Test' -Dkarate.tags="@regression"

# Exclude integration tests
./gradlew test --tests '*Test' -Dkarate.tags="not @integration"

# Positive tests only
./gradlew test --tests '*Test' -Dkarate.tags="@positive"

# Negative/validation tests
./gradlew test --tests '*Test' -Dkarate.tags="@negative"
```

### Run with Specific Configuration
```bash
# Use different base URL
./gradlew test -Dkarate.env=prod -Dkarate.base.url=https://api.production.com
```

---

## Test Tags and Coverage

### Coverage by Category

| Tag | Count | Purpose |
|-----|-------|---------|
| @smoke | 10 | Quick validation of primary flows |
| @regression | 95 | Full regression test suite |
| @positive | 60 | Valid request scenarios |
| @negative | 25 | Invalid request handling |
| @validation | 15 | Parameter validation |
| @performance | 10 | Response time verification |
| @offline | 8 | Offline mode testing |
| @caching | 12 | Cache mechanism testing |
| @error-handling | 10 | Error scenario testing |
| @integration | 12 | End-to-end scenarios |

### Total Test Scenarios: 95+ BDD tests

---

## Configuration

### karate-config.js

Global configuration file that sets:
- Base URL: `http://localhost:8080`
- API version: `/api/v1`
- Timeouts: 5s connect, 10s read
- Test data (cities, routes, stops, coordinates)
- Max response time expectations

```javascript
// Override in environment-specific config:
// karate-config-dev.js
// karate-config-prod.js
```

---

## Key Features Tested

### 1. **Data Validation**
- Response structure compliance
- Field type validation
- Enum value validation
- Numeric ranges verification
- Timestamp format validation

### 2. **Functionality**
- CRUD operations on cache
- Data filtering by route/city/stop
- Offline mode fallback
- Error handling and recovery
- Multi-endpoint workflows

### 3. **Performance**
- Response time < 5 seconds (general)
- Response time < 3 seconds (specific endpoints)
- Cache hit rate metrics
- Sequential call performance

### 4. **Resilience**
- Graceful degradation
- Stale cache usage
- Offline mock data
- Error recovery
- Data source fallback chain

### 5. **Security**
- SQL injection prevention
- XSS prevention
- Parameter validation
- CORS headers
- Content-type validation

---

## Test Execution Report

After running tests, review:
- `build/test-results/test/` - XML test reports
- `build/reports/tests/test/` - HTML test report
- Console output for detailed assertion failures

Open `build/reports/tests/test/index.html` in browser for visual report.

---

## Continuous Integration

### GitHub Actions Example
```yaml
name: BDD Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK
        uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Run Karate tests
        run: ./gradlew test -Dkarate.tags="@regression"
```

---

## Troubleshooting

### Tests Fail with Connection Error
- Ensure backend API is running on `http://localhost:8080`
- Check firewall and port availability
- Verify `baseUrl` in karate-config.js

### Tests Timeout
- Increase timeout values in karate-config.js
- Check API response times
- Verify network connectivity

### Flaky Tests
- Check if endpoints have caching enabled
- Add delays between dependent tests
- Verify test data isolation

---

## Best Practices

1. **Test Independence**: Each test should be independent and not rely on other tests
2. **Data Cleanup**: Use `@Before` hooks to clean cache if needed
3. **Realistic Scenarios**: Use valid test data (actual NYC stops, routes)
4. **Performance Baselines**: Establish and monitor response time expectations
5. **Error Coverage**: Test both positive and negative scenarios
6. **Documentation**: Keep test descriptions clear and updated

---

## Future Enhancements

- [ ] Load testing with Karate performance runner
- [ ] API mock server for isolated testing
- [ ] Automated performance regression detection
- [ ] Custom domain-specific language (DSL) helpers
- [ ] Contract testing with provider/consumer
- [ ] Visual regression testing for responses

---

## References

- [Karate Documentation](https://github.com/intuit/karate)
- [Karate Examples](https://github.com/intuit/karate/tree/master/karate-demo)
- [API Testing Best Practices](https://github.com/intuit/karate/wiki)
- [BDD in Practice](https://cucumber.io/)

---

**Last Updated:** May 2024  
**Test Framework Version:** Karate 1.4.1  
**Java Version:** 17+
