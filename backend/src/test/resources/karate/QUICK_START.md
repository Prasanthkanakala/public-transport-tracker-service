# Test Execution Quick Guide

## Quick Commands

### Run All Tests
```bash
./gradlew test
```

### Run Specific Feature
```bash
./gradlew test --tests TransportApiTest
./gradlew test --tests RoutingApiTest
./gradlew test --tests CacheApiTest
./gradlew test --tests ErrorHandlingTest
./gradlew test --tests IntegrationTest
```

### Run By Tag
```bash
# Smoke tests only
./gradlew test -Dkarate.tags="@smoke"

# Regression tests
./gradlew test -Dkarate.tags="@regression"

# Positive tests
./gradlew test -Dkarate.tags="@positive"

# Integration tests
./gradlew test -Dkarate.tags="@integration"

# Performance tests
./gradlew test -Dkarate.tags="@performance"
```

### Run Specific Scenario
```bash
./gradlew test -Dkarate.scenarios="Get all transport data for a city"
```

## Test Results

View HTML report:
```
build/reports/tests/test/index.html
```

View XML report:
```
build/test-results/test/TEST-com.transport.tracker.bdd.*.xml
```

## Coverage Summary

| Test Suite | Scenarios | Tags |
|-----------|-----------|------|
| Transport API | 10 | @transport, @smoke, @regression |
| Arrivals API | 10 | @arrivals, @smoke, @regression |
| Vehicles API | 11 | @vehicles, @smoke, @regression |
| Alerts API | 11 | @alerts, @smoke, @regression |
| Routing API | 14 | @routing, @smoke, @regression |
| Crowding API | 11 | @crowding, @smoke, @regression |
| Cache API | 12 | @cache, @smoke, @regression |
| Error Handling | 20 | @error-scenarios, @negative, @validation |
| Integration | 15 | @integration, @smoke, @regression |
| **TOTAL** | **95+** | Multi-tagged |

## Tag Reference

- **@smoke** - Fast smoke tests (10-15 seconds)
- **@regression** - Full regression suite (2-5 minutes)
- **@positive** - Valid scenarios
- **@negative** - Error/edge cases
- **@validation** - Input validation
- **@performance** - Response time checks
- **@offline** - Offline mode scenarios
- **@caching** - Cache behavior
- **@integration** - End-to-end flows
- **@hateoas** - HATEOAS link validation
- **@realtime** - Real-time data
- **@stress** - Stress/load scenarios

## Environment Configuration

Test environment defaults to `http://localhost:8080`. Override:

```bash
# Development
./gradlew test -Dkarate.env=dev

# Staging
./gradlew test -Dkarate.env=staging

# Production
./gradlew test -Dkarate.env=prod
```

## Debugging

### Print Response Details
```gherkin
* print response
* print response.metadata
* print response.data
```

### Add Breakpoints (in IDE)
```gherkin
* debug()
```

### Verbose Logging
```bash
./gradlew test -Dkarate.loglevel=debug
```

## Performance Baseline

Expected response times (95th percentile):
- `/transport` - 3000ms
- `/arrivals` - 2000ms
- `/vehicles` - 2500ms
- `/alerts` - 2000ms
- `/plan` - 4000ms
- `/crowding` - 2500ms
- `/cache/stats` - 500ms

## CI/CD Integration

Run in CI pipeline:
```bash
./gradlew test -Dkarate.tags="@regression" --no-daemon
```

Generate report for CI:
```bash
./gradlew test -Dkarate.tags="@regression" -x test && ./gradlew testReport
```

## Common Issues

**Connection refused?**
- Check if `http://localhost:8080` is accessible
- Verify backend is running

**Timeout errors?**
- Increase timeout in karate-config.js
- Check network/API performance

**Assertion failures?**
- Review test output for actual vs expected values
- Check test data setup
- Verify API response format

**Cache-related flakes?**
- Clear cache before running tests
- Use offline=true for deterministic results

---
For detailed information, see [README.md](README.md)
