# API Reference – Public Transport Tracker

**Base URL:** `http://localhost:8080`  
**API Version:** `v1`  
**Swagger UI:** `http://localhost:8080/swagger-ui/index.html`  
**OpenAPI Spec:** `http://localhost:8080/v3/api-docs`

All responses are wrapped in the HAL/HATEOAS envelope below. Every response includes `metadata` (information about data freshness) and `_links` (navigation links to related resources).

---

## Table of Contents

1. [Response Envelope & Metadata](#response-envelope)
2. [Core Transport Endpoints](#core-transport-endpoints)
3. [Cache Management Endpoints](#cache-management)
4. [Error Handling](#error-handling)
5. [Data Models](#data-models)
6. [Quick Examples](#quick-examples)

---

## Response Envelope

Every successful response (200-399) follows this structure:

```json
{
  "data": { /* Actual response data (vehicles, alerts, arrivals, etc.) */ },
  "metadata": {
    "cached": false,
    "cacheAgeSeconds": null,
    "dataSource": "LIVE | CACHE | STALE_CACHE | MOCK",
    "timestamp": "2026-05-07T10:30:00Z",
    "city": "nyc",
    "routeId": "A",
    "offlineMode": false,
    "provider": "MTA | TRANSIT_LAND | SEPTA"
  },
  "_links": {
    "self":      { "href": "/api/v1/transport?city=nyc&routeId=A", "method": "GET" },
    "vehicles":  { "href": "/api/v1/transport/vehicles?city=nyc&routeId=A", "method": "GET" },
    "alerts":    { "href": "/api/v1/transport/alerts?city=nyc", "method": "GET" },
    "arrivals":  { "href": "/api/v1/transport/arrivals?city=nyc&routeId=A", "method": "GET" },
    "crowding":  { "href": "/api/v1/transport/crowding?city=nyc&routeId=A", "method": "GET" },
    "plan":      { "href": "/api/v1/transport/plan?from=ORIGIN&to=DEST&city=nyc", "method": "GET" },
    "cache":     { "href": "/api/v1/cache/stats", "method": "GET" }
  }
}
```

### Metadata Explained

| Field | Type | Meaning |
|-------|------|---------|
| `cached` | boolean | Is this data from cache? (vs fresh/live) |
| `cacheAgeSeconds` | number/null | Age of cached data. `null` if not cached. |
| `dataSource` | string | One of: `LIVE` (fresh API call), `CACHE` (< 5 min old), `STALE_CACHE` (< 1 hr old), `MOCK` (sample data) |
| `timestamp` | ISO 8601 datetime | When this response was generated |
| `city` | string | Requested city (`nyc`, `philly`, etc.) |
| `routeId` | string | Requested route (or null if not applicable) |
| `offlineMode` | boolean | Whether offline mode was active |
| `provider` | string | Which API provided the data (MTA, TransitLand, SEPTA, TfL) |

**Understanding data freshness:**
- **LIVE** (green badge) — Data is brand new from the API (< 5 seconds old)
- **CACHE** (blue badge) — Data is cached (5 seconds - 5 minutes old), still considered current
- **STALE_CACHE** (yellow badge) — Cached data past TTL (5-60 minutes old), shown when APIs are unavailable
- **MOCK** (gray badge) — Sample/demonstration data, shown when offline mode is active or all APIs fail

---

## Core Transport Endpoints

### 1. `GET /api/v1/transport`

**Summary:** Fetch all transport data for a city/route (vehicles, arrivals, alerts, crowding, and conditional alerts).

**Parameters:**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `city` | string | No | `"nyc"` | City identifier: `nyc`, `philly`, `london` |
| `routeId` | string | No | null | Route identifier (case-insensitive): `A`, `1`, `M15`, `4B` |
| `offline` | boolean | No | false | Force offline mode: returns mock data immediately |

**Response: 200 OK**
```json
{
  "data": {
    "city": "nyc",
    "routeId": "A",
    "vehicles": [
      {
        "vehicleId": "VEH-A-001",
        "routeId": "A",
        "latitude": 40.7589,
        "longitude": -73.9851,
        "bearing": 180.0,
        "speedKmh": 32.5,
        "delaySeconds": 960,
        "occupancyLevel": "FEW_SEATS_AVAILABLE",
        "timestamp": "2026-05-07T10:30:00Z"
      }
    ],
    "arrivals": [
      {
        "stopId": "A27N",
        "stopName": "Times Square-42 St",
        "routeId": "A",
        "headsign": "Far Rockaway-Mott Av",
        "scheduledArrival": "2026-05-07T10:35:00Z",
        "predictedArrival": "2026-05-07T10:51:00Z",
        "delaySeconds": 960,
        "minutesToArrival": 19
      }
    ],
    "alerts": [
      {
        "alertId": "ALERT-001",
        "type": "DISRUPTION",
        "effect": "MODIFIED_SERVICE",
        "cause": "SCHEDULED_MAINTENANCE",
        "description": "Service running on modified schedule",
        "activeFrom": "2026-05-07T06:00:00Z",
        "activeTo": "2026-05-07T22:00:00Z"
      }
    ],
    "crowding": [
      {
        "vehicleId": "VEH-A-001",
        "routeId": "A",
        "occupancyLevel": "FEW_SEATS_AVAILABLE",
        "percentFull": 65
      }
    ],
    "conditionalAlerts": [
      {
        "type": "DELAY",
        "level": "WARNING",
        "message": "Significant delays - Plan accordingly",
        "icon": "clock-alert"
      }
    ]
  },
  "metadata": { /* see Response Envelope above */ },
  "_links": { /* see Response Envelope above */ }
}
```

**Status Codes:**
- `200 OK` — Data retrieved successfully (from any source: live, cache, stale, or mock)
- `400 Bad Request` — Invalid city or parameter
- `503 Service Unavailable` — All APIs failed AND no stale cache available (extremely rare)

---

### 2. `GET /api/v1/transport/vehicles`

**Summary:** Real-time vehicle locations and movement data.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `city` | string | No | City: `nyc`, `philly`, `london` |
| `routeId` | string | No | Route: `A`, `1`, etc. |
| `offline` | boolean | No | Force mock data |

**Response: 200 OK**
```json
{
  "data": [
    {
      "vehicleId": "VEH-A-001",
      "routeId": "A",
      "tripId": "TRIP-A-MORNING-001",
      "latitude": 40.7589,
      "longitude": -73.9851,
      "bearing": 180.0,
      "speedKmh": 32.5,
      "status": "IN_TRANSIT_TO",
      "currentStopId": "A27N",
      "nextStopId": "A28S",
      "delaySeconds": 960,
      "delayLabel": "16 min late",
      "occupancyLevel": "FEW_SEATS_AVAILABLE",
      "timestamp": "2026-05-07T10:30:00Z"
    }
  ],
  "metadata": { /* ... */ },
  "_links": { /* ... */ }
}
```

**Vehicle Fields:**

| Field | Type | Meaning |
|-------|------|---------|
| `vehicleId` | string | Unique vehicle identifier |
| `routeId` | string | Which route this vehicle operates |
| `latitude` / `longitude` | number | GPS position |
| `bearing` | number | Direction (0=North, 90=East, 180=South, 270=West) |
| `speedKmh` | number | Current speed in kilometers per hour |
| `status` | enum | Vehicle status: `IN_TRANSIT_TO`, `STOPPED_AT`, `INCOMING_AT` |
| `delaySeconds` | number | How many seconds late (negative = early) |
| `occupancyLevel` | enum | Crowding: `EMPTY`, `CROWDED`, `HIGH`, `FULL` |
| `timestamp` | ISO 8601 | When this position was recorded |

---

### 3. `GET /api/v1/transport/arrivals`

**Summary:** Arrival predictions (ETA) for a specific stop.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `stopId` | string | **Yes** | Stop identifier: `A27N`, `STOP-001` |
| `routeId` | string | No | Filter by specific route |
| `offline` | boolean | No | Force mock data |

**Response: 200 OK**
```json
{
  "data": [
    {
      "stopId": "A27N",
      "stopName": "Times Square-42 St",
      "routeId": "A",
      "routeName": "A Train",
      "headsign": "Far Rockaway-Mott Av",
      "scheduledArrival": "2026-05-07T10:35:00Z",
      "predictedArrival": "2026-05-07T10:51:00Z",
      "delaySeconds": 960,
      "status": "DELAYED",
      "platform": "1",
      "minutesToArrival": 19,
      "realtime": true
    }
  ],
  "metadata": { /* ... */ },
  "_links": { /* ... */ }
}
```

**Error: 400 Bad Request** (when `stopId` is missing)
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'stopId' is missing"
}
```

---

### 4. `GET /api/v1/transport/alerts`

**Summary:** Active service alerts for a city.

**Parameters:**

| Name | Type | Required | Description | Example |
|------|------|----------|-------------|---------|
| city | string | No | City identifier | `nyc` |
| offline | boolean | No | Offline mode | `true` |

**Response: 200 OK**
```json
{
  "data": [
    {
      "alertId":         "ALT-MTA-2024-001",
      "type":            "DISRUPTION",
      "severity":        "HIGH",
      "headerText":      "A/C trains suspended between Jay St and Atlantic Av",
      "descriptionText": "Use the F train as an alternative...",
      "displayMessage":  "Service alert - Check alternative routes",
      "affectedRoutes":  ["A", "C"],
      "affectedStops":   ["A38N", "A40N"],
      "cause":           "MAINTENANCE",
      "effect":          "SUSPENSION",
      "activeFrom":      "2024-01-15T06:00:00Z",
      "activeUntil":     "2024-01-15T14:00:00Z"
    }
  ]
}
```

**Alert type values:** `DELAY`, `DISRUPTION`, `WEATHER`, `PLANNED_WORK`, `GENERAL_INFO`  
**Severity values:** `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`  
**Cause values:** `TECHNICAL_PROBLEM`, `ACCIDENT`, `WEATHER`, `MAINTENANCE`, `CONSTRUCTION`, `OTHER`  
**Effect values:** `DETOUR`, `STOP_MOVED`, `SERVICE_CHANGE`, `SUSPENSION`, `SIGNIFICANT_DELAYS`, `REDUCED_SERVICE`

---

### 5. `GET /api/v1/transport/plan`

**Summary:** Plan a journey from origin to destination.

**Parameters:**

| Name | Type | Required | Description | Example |
|------|------|----------|-------------|---------|
| **from** | string | **Yes** | Origin stop name or ID | `Times Square-42 St` |
| **to** | string | **Yes** | Destination stop name or ID | `Atlantic Av-Barclays Ctr` |
| city | string | No | City identifier | `nyc` |
| offline | boolean | No | Offline mode | `false` |

**Response: 200 OK**
```json
{
  "data": [
    {
      "planId":          "PLAN-A1B2C3D4",
      "origin":          "Times Square-42 St",
      "destination":     "Atlantic Av-Barclays Ctr",
      "city":            "nyc",
      "departureTime":   "2024-01-15T08:35:00Z",
      "arrivalTime":     "2024-01-15T09:01:00Z",
      "durationMinutes": 26,
      "transfers":       0,
      "status":          "OPTIMAL",
      "confidence":      0.85,
      "walkingDistanceMeters": 200,
      "legs": [
        {
          "mode":          "SUBWAY",
          "routeId":       "A",
          "routeName":     "A Train",
          "headsign":      "Far Rockaway-Mott Av",
          "fromStopId":    "A27N",
          "fromStopName":  "Times Square-42 St",
          "toStopId":      "A41N",
          "toStopName":    "Atlantic Av-Barclays Ctr",
          "departureTime": "2024-01-15T08:35:00Z",
          "arrivalTime":   "2024-01-15T09:01:00Z",
          "durationMinutes": 26,
          "numStops":      6,
          "delaySeconds":  0
        }
      ]
    }
  ]
}
```

**Plan status values:**
- `OPTIMAL` – best available route
- `ALTERNATIVE` – secondary option (e.g. with transfer)
- `DISRUPTED` – route is affected by active alerts

---

### 6. `GET /api/v1/transport/crowding`

**Summary:** Crowding and occupancy per vehicle on a route.

**Parameters:**

| Name | Type | Required | Description | Example |
|------|------|----------|-------------|---------|
| **routeId** | string | **Yes** | Route identifier | `A` |
| city | string | No | City identifier | `nyc` |
| offline | boolean | No | Offline mode | `false` |

**Response: 200 OK** – Returns full `TransportData` including `crowding` field.

**Crowding level values:**
- `LOW` – < 50% capacity
- `MEDIUM` – 50–75%
- `HIGH` – 75–90%
- `FULL` – > 90%

---

## Cache Management

### 7. `GET /api/v1/cache/stats`

Returns current cache statistics.

**Response:**
```json
{
  "size": 12,
  "hits": 245,
  "misses": 18,
  "staleHits": 3,
  "hitRate": "93.17%",
  "evictions": 5,
  "ttlSeconds": 300,
  "staleTtlSeconds": 3600
}
```

### 8. `DELETE /api/v1/cache`

**Summary:** Clear all cached data (useful for debugging or forcing fresh data refresh).

**Response: 200 OK**
```json
{ "message": "Cache cleared successfully", "entriesDeleted": 12 }
```

---

## Error Handling

All error responses follow this envelope:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'stopId' is missing",
  "path": "/api/v1/transport/arrivals",
  "timestamp": "2026-05-07T10:30:00Z"
}
```

**Status Codes:**

| Code | Reason | Cause |
|------|--------|-------|
| `200` | OK | Request succeeded |
| `400` | Bad Request | Missing required parameter or invalid format |
| `404` | Not Found | Endpoint doesn't exist |
| `500` | Internal Server Error | Unexpected backend error (extremely rare with our resilience chain) |
| `503` | Service Unavailable | All APIs failed AND no stale cache (emergency fallback) |

---

## Data Models

### VehicleLocation
```json
{
  "vehicleId": "VEH-A-001",
  "routeId": "A",
  "tripId": "TRIP-A-MORNING-001",
  "latitude": 40.7589,
  "longitude": -73.9851,
  "bearing": 180.0,
  "speedKmh": 32.5,
  "status": "IN_TRANSIT_TO",
  "currentStopId": "A27N",
  "nextStopId": "A28S",
  "delaySeconds": 960,
  "occupancyLevel": "FEW_SEATS_AVAILABLE",
  "timestamp": "2026-05-07T10:30:00Z"
}
```

### ArrivalPrediction
```json
{
  "stopId": "A27N",
  "stopName": "Times Square-42 St",
  "routeId": "A",
  "routeName": "A Train",
  "headsign": "Far Rockaway-Mott Av",
  "scheduledArrival": "2026-05-07T10:35:00Z",
  "predictedArrival": "2026-05-07T10:51:00Z",
  "delaySeconds": 960,
  "status": "DELAYED",
  "platform": "1",
  "minutesToArrival": 19,
  "realtime": true
}
```

### ServiceAlert
```json
{
  "alertId": "ALT-MTA-2024-001",
  "type": "DISRUPTION",
  "severity": "HIGH",
  "headerText": "A/C trains suspended between Jay St and Atlantic Av",
  "descriptionText": "Use the F train as an alternative...",
  "displayMessage": "Service alert - Check alternative routes",
  "affectedRoutes": ["A", "C"],
  "affectedStops": ["A38N", "A40N"],
  "cause": "MAINTENANCE",
  "effect": "SUSPENSION",
  "activeFrom": "2026-05-07T06:00:00Z",
  "activeUntil": "2026-05-07T14:00:00Z"
}
```

### CrowdingInfo
```json
{
  "vehicleId": "VEH-A-001",
  "routeId": "A",
  "occupancyLevel": "FEW_SEATS_AVAILABLE",
  "percentFull": 65
}
```

### AlertMessage (Conditional)
```json
{
  "type": "DELAY",
  "level": "WARNING",
  "message": "Significant delays - Plan accordingly",
  "icon": "clock-alert"
}
```

Possible types: `DELAY`, `DISRUPTION`, `CROWDING`, `WEATHER`

### RoutePlan
```json
{
  "planId": "PLAN-A1B2C3D4",
  "origin": "Times Square-42 St",
  "destination": "Atlantic Av-Barclays Ctr",
  "city": "nyc",
  "departureTime": "2026-05-07T08:35:00Z",
  "arrivalTime": "2026-05-07T09:01:00Z",
  "durationMinutes": 26,
  "transfers": 0,
  "status": "OPTIMAL",
  "confidence": 0.85,
  "walkingDistanceMeters": 200,
  "legs": [
    {
      "mode": "SUBWAY",
      "routeId": "A",
      "routeName": "A Train",
      "headsign": "Far Rockaway-Mott Av",
      "fromStopId": "A27N",
      "fromStopName": "Times Square-42 St",
      "toStopId": "A41N",
      "toStopName": "Atlantic Av-Barclays Ctr",
      "departureTime": "2026-05-07T08:35:00Z",
      "arrivalTime": "2026-05-07T09:01:00Z",
      "durationMinutes": 26,
      "numStops": 6,
      "delaySeconds": 0
    }
  ]
}
```

---

## Quick Examples

### Example 1: Fetch Live Data for NYC Route A

```bash
curl -X GET "http://localhost:8080/api/v1/transport?city=nyc&routeId=A"
```

Response includes fresh vehicle positions, arrivals, alerts, and conditional alerts. `dataSource: "LIVE"`

### Example 1.5: Fetch Live Data for London Tube

```bash
curl -X GET "http://localhost:8080/api/v1/transport?city=london&routeId=piccadilly"
```

Returns TfL data for London Underground Piccadilly line. `provider: "TfL"`

### Example 2: Get Arrivals for Times Square Stop

```bash
curl -X GET "http://localhost:8080/api/v1/transport/arrivals?stopId=A27N"
```

Returns all arrival predictions for that stop across all routes.

### Example 3: Plan a Journey

```bash
curl -X GET "http://localhost:8080/api/v1/transport/plan?from=Times+Square-42+St&to=Atlantic+Av-Barclays+Ctr&city=nyc"
```

Returns optimal and alternative routes with transfer information.

### Example 4: Offline Testing

```bash
curl -X GET "http://localhost:8080/api/v1/transport?city=nyc&routeId=A&offline=true"
```

Returns mock data immediately (useful for frontend testing).

### Example 5: Check Cache Status

```bash
curl -X GET "http://localhost:8080/api/v1/cache/stats"
```

Returns cache hit rate and statistics for monitoring.

---

## Response Codes Cheat Sheet

| Scenario | Status | dataSource |
|----------|--------|-----------|
| Fresh API data | 200 | LIVE |
| Cached < 5 min | 200 | CACHE |
| APIs down, stale cache available | 200 | STALE_CACHE |
| Offline mode active | 200 | MOCK |
| All APIs failed, no cache | 503 | — |
| Missing required parameter | 400 | — |

---

## Authentication & Security

Currently, all endpoints are **public** (no authentication required). For production deployment, add:

- **API Key Header**: `Authorization: Bearer YOUR_API_KEY`
- **Rate Limiting**: Max 100 requests/minute per IP
- **SSL/TLS**: All traffic over HTTPS

See `SecurityConfig.java` for current security headers:
- `X-Content-Type-Options: nosniff` – prevents MIME sniffing
- `X-Frame-Options: DENY` – prevents clickjacking
- CSRF protection disabled (stateless API)

Clears all cached entries. Next requests will hit live APIs.

### 9. `DELETE /api/v1/cache/entry?city={city}&routeId={routeId}`

Invalidates a specific cache key.

---

## Health Endpoints (Spring Actuator)

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Service health (UP/DOWN) |
| `GET /actuator/info` | Application version and description |
| `GET /actuator/metrics` | JVM and application metrics |

---

## Error Responses

All errors follow this structure:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'stopId' is missing",
  "path": "/api/v1/transport/arrivals",
  "timestamp": "2024-01-15T08:30:00Z"
}
```

| HTTP Status | Meaning |
|-------------|---------|
| 400 | Missing required parameter or validation failure |
| 503 | All upstream transit APIs are unavailable (rare – normally falls back to mock) |
| 500 | Unexpected server error |