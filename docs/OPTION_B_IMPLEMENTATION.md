# Option B Implementation: Observability & Ops

## Why Option B Was Chosen Over Options A & C

### Decision Rationale

**Option A (Resilience & Offline Mode)** was already implemented in the initial version of this service. The system currently features:
- In-memory cache with TTL
- Stale data serving on upstream failure
- Explicit degradation strategy (OFFLINE → CACHE → LIVE → STALE_CACHE → MOCK)
- Comprehensive offline mode support

**Option C (Data Reasoning)** would focus on:
- ETA calculation algorithms
- Trust boundaries of transit data
- Handling missing/delayed vehicle updates

While valuable, Option C is more domain-specific and doesn't address the critical operational needs of a production microservice.

**Option B (Observability & Ops)** was selected because:
1. **Production Readiness**: A service without observability is a black box in production
2. **Operational Excellence**: Enables proactive monitoring, debugging, and incident response
3. **Complementary to Option A**: Enhances the existing resilience features with visibility
4. **Industry Best Practice**: Observability is a cornerstone of modern microservices architecture
5. **SRE Alignment**: Enables SLO/SLI tracking for reliability engineering

---

## Implementation Components

### 1. Structured Logging

**Technology**: Logback with JSON encoder (logstash-logback-encoder)

**Features**:
- JSON-formatted logs for machine parsing
- Contextual fields: `requestId`, `userId`, `city`, `routeId`, `dataSource`
- Log levels: ERROR, WARN, INFO, DEBUG
- MDC (Mapped Diagnostic Context) for request tracing
- Correlation IDs for distributed tracing

**Log Structure**:
```json
{
  "timestamp": "2024-04-26T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.transport.tracker.service.TransportService",
  "message": "Successfully fetched live data",
  "requestId": "abc-123-def",
  "city": "nyc",
  "routeId": "Q",
  "dataSource": "LIVE",
  "provider": "MTA",
  "latencyMs": 245
}
```

### 2. Metrics Collection

**Technology**: Micrometer + Prometheus

**Metrics Tracked**:

#### Business Metrics
- `transport.requests.total` (Counter) - Total API requests by endpoint
- `transport.data.source` (Counter) - Data source usage (LIVE, CACHE, STALE_CACHE, MOCK)
- `transport.cache.hits` (Counter) - Cache hit count
- `transport.cache.misses` (Counter) - Cache miss count
- `transport.api.provider` (Counter) - API provider usage (MTA, TransitLand, SEPTA, TfL)

#### Technical Metrics
- `transport.api.latency` (Timer) - API call latency by provider
- `transport.api.failures` (Counter) - API failure count by provider and error type
- `transport.cache.size` (Gauge) - Current cache size
- `transport.cache.evictions` (Counter) - Cache eviction count
- `http.server.requests` (Timer) - HTTP request duration (Spring Boot Actuator default)

#### SLI Metrics (for SLO calculation)
- `transport.requests.successful` (Counter) - Successful requests (2xx responses)
- `transport.requests.failed` (Counter) - Failed requests (5xx responses)
- `transport.latency.p95` (Histogram) - 95th percentile latency
- `transport.latency.p99` (Histogram) - 99th percentile latency

### 3. Health & Readiness Endpoints

**Spring Boot Actuator Endpoints**:

#### `/actuator/health`
**Purpose**: Overall application health
**Response**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "transitApis": {
      "status": "UP",
      "details": {
        "mta": "UP",
        "transitland": "UP",
        "septa": "DOWN"
      }
    },
    "cache": {
      "status": "UP",
      "details": {
        "size": 42,
        "maxSize": 1000,
        "utilizationPercent": 4.2
      }
    }
  }
}
```

#### `/actuator/health/readiness`
**Purpose**: Kubernetes readiness probe
**Criteria**: 
- At least one transit API is available
- Cache service is operational
- Application context is fully loaded

#### `/actuator/health/liveness`
**Purpose**: Kubernetes liveness probe
**Criteria**:
- JVM is running
- Application is not deadlocked
- Critical threads are responsive

#### `/actuator/metrics`
**Purpose**: Prometheus metrics scraping endpoint
**Format**: Prometheus text format

#### `/actuator/info`
**Purpose**: Application metadata
**Response**:
```json
{
  "app": {
    "name": "Public Transport Tracker",
    "version": "1.0.0",
    "description": "Real-time public transport tracking microservice"
  },
  "build": {
    "time": "2024-04-26T10:00:00Z",
    "artifact": "transport-tracker",
    "group": "com.transport"
  }
}
```

### 4. Service Level Objectives (SLOs)

#### SLO Definitions

**SLO 1: Availability**
- **Objective**: 99.5% of requests return a successful response (2xx or 3xx)
- **Measurement Window**: 30 days
- **Error Budget**: 0.5% = ~3.6 hours downtime/month
- **SLI**: `(successful_requests / total_requests) * 100`
- **Implementation**: Count 2xx/3xx vs 5xx responses

**SLO 2: Latency (P95)**
- **Objective**: 95% of requests complete within 500ms
- **Measurement Window**: 7 days
- **SLI**: 95th percentile response time
- **Implementation**: Histogram buckets: [50, 100, 200, 500, 1000, 2000, 5000]ms

**SLO 3: Latency (P99)**
- **Objective**: 99% of requests complete within 1000ms
- **Measurement Window**: 7 days
- **SLI**: 99th percentile response time

**SLO 4: API Success Rate**
- **Objective**: 95% of upstream API calls succeed (or gracefully degrade)
- **Measurement Window**: 24 hours
- **SLI**: `(successful_api_calls / total_api_calls) * 100`
- **Note**: Graceful degradation (serving cache/mock) counts as success

**SLO 5: Cache Hit Rate**
- **Objective**: 60% cache hit rate during normal operations
- **Measurement Window**: 24 hours
- **SLI**: `(cache_hits / (cache_hits + cache_misses)) * 100`
- **Purpose**: Reduce upstream API load and improve latency

#### Error Budget Policy

| Error Budget Remaining | Action |
|------------------------|--------|
| > 50% | Safe to deploy new features |
| 20-50% | Increase monitoring, reduce deployment frequency |
| 5-20% | Freeze feature releases, focus on reliability |
| < 5% | Incident response mode, rollback recent changes |

#### Alerting Thresholds

- **Critical**: SLO burn rate > 10x (will exhaust error budget in 3 days)
- **Warning**: SLO burn rate > 5x (will exhaust error budget in 6 days)
- **Info**: SLO burn rate > 2x (will exhaust error budget in 15 days)

---

## Technical Implementation Details

### Dependencies Added

```gradle
// Metrics and observability
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'io.micrometer:micrometer-core'

// Structured logging
implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
```

### Configuration Files

1. **application.properties** - Actuator and metrics configuration
2. **logback-spring.xml** - Structured logging configuration
3. **MetricsConfig.java** - Custom metrics beans
4. **HealthIndicators** - Custom health checks

### Custom Components

1. **TransitApiHealthIndicator** - Monitors upstream API health
2. **CacheHealthIndicator** - Monitors cache status
3. **MetricsService** - Centralized metrics recording
4. **RequestLoggingFilter** - Adds correlation IDs and MDC context
5. **SloCalculator** - Computes SLO compliance in real-time

---

## Monitoring Dashboard (Grafana)

### Recommended Panels

1. **Request Rate** - Requests/second over time
2. **Error Rate** - 5xx responses/second
3. **Latency Heatmap** - P50, P95, P99 latencies
4. **Data Source Distribution** - Pie chart (LIVE, CACHE, STALE, MOCK)
5. **API Provider Health** - Status of MTA, TransitLand, SEPTA, TfL
6. **Cache Performance** - Hit rate, size, evictions
7. **SLO Compliance** - Current vs target for each SLO
8. **Error Budget Burn** - Remaining error budget over time

---

## Production Deployment Checklist

- [ ] Prometheus server configured to scrape `/actuator/prometheus`
- [ ] Grafana dashboards imported
- [ ] Alerting rules configured in Prometheus/Alertmanager
- [ ] Log aggregation (ELK/Loki) ingesting JSON logs
- [ ] Kubernetes probes using `/actuator/health/readiness` and `/actuator/health/liveness`
- [ ] SLO dashboards visible to team
- [ ] On-call rotation defined with escalation policy
- [ ] Runbooks created for common alerts

---

## Future Enhancements

1. **Distributed Tracing**: Add OpenTelemetry/Jaeger for request tracing across services
2. **Custom Dashboards**: Build service-specific Grafana dashboards
3. **Anomaly Detection**: ML-based anomaly detection on metrics
4. **Cost Tracking**: Track API call costs per provider
5. **User Journey Metrics**: Track end-to-end user flows

---

## References

- [Google SRE Book - SLOs](https://sre.google/sre-book/service-level-objectives/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
- [The Four Golden Signals](https://sre.google/sre-book/monitoring-distributed-systems/)
