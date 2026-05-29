# Test Data Reference

## Cities
- New York (NYC-MTA provider)
- London (TfL provider)
- Philadelphia (SEPTA provider)

## Routes
- **NYC**: M1, M2, M5, etc.
- **London**: 1, 25, Route-25, etc.
- **Philadelphia**: Market-Frankford Line, etc.

## Stop IDs
- 40001 - Penn Station
- 40002 - Herald Square
- 40003 - Times Square

## Test Coordinates

### New York City
```
Origin (Times Square):
  Lat: 40.7128
  Lon: -74.0060

Destination (Central Park):
  Lat: 40.7614
  Lon: -73.9776
```

### London
```
Origin (Piccadilly Circus):
  Lat: 51.5102
  Lon: -0.1349

Destination (Leicester Square):
  Lat: 51.5111
  Lon: -0.1281
```

### Philadelphia
```
Origin (City Hall):
  Lat: 39.9526
  Lon: -75.1652

Destination (Independence Hall):
  Lat: 39.9486
  Lon: -75.1501
```

## Expected Response Values

### Vehicle Status
- IN_TRANSIT_TO
- STOPPED_AT
- INCOMING_AT

### Arrival Status
- ON_TIME
- DELAYED
- EARLY
- CANCELLED
- NO_DATA

### Alert Type
- DELAY
- DISRUPTION
- WEATHER
- CROWDING
- PLANNED_WORK
- GENERAL_INFO

### Alert Severity
- LOW
- MEDIUM
- HIGH
- CRITICAL

### Alert Effect
- DETOUR
- STOP_MOVED
- SERVICE_CHANGE
- SUSPENSION
- SIGNIFICANT_DELAYS
- REDUCED_SERVICE

### Crowding Level
- LOW
- MEDIUM
- HIGH
- FULL

### GTFS Occupancy Status
- EMPTY
- MANY_SEATS
- FEW_SEATS
- STANDING_ONLY
- CRUSHED
- FULL

### Data Source
- LIVE
- CACHE
- STALE_CACHE
- MOCK
- OFFLINE

### Route Plan Status
- OPTIMAL
- ALTERNATIVE
- DISRUPTED
- NO_SERVICE

## Performance Thresholds
- General API: < 5000ms
- Specific endpoints: < 3000ms
- Cache operations: < 1000ms
- Response time for 95th percentile

## Cache Configuration
- Default TTL: 300 seconds (5 minutes)
- Stale TTL: 3600 seconds (1 hour)
- Max capacity: 1000 entries
- Eviction strategy: LRU (Least Recently Used)

## Sample Requests

### Get Arrivals
```
GET /api/v1/transport/arrivals?stopId=40001&routeId=M1
```

### Get Vehicles
```
GET /api/v1/transport/vehicles?city=New%20York&routeId=M1
```

### Get Alerts
```
GET /api/v1/transport/alerts?city=New%20York
```

### Plan Route
```
GET /api/v1/transport/plan?from=40.7128,-74.0060&to=40.7614,-73.9776&city=New%20York
```

### Get Crowding
```
GET /api/v1/transport/crowding?routeId=M1&city=New%20York
```

### Cache Stats
```
GET /api/v1/cache/stats
```

### Clear Cache
```
DELETE /api/v1/cache
```

### Delete Cache Entry
```
DELETE /api/v1/cache/entry?city=New%20York&route=M1
```

## Response Metadata Template

```json
{
  "metadata": {
    "cached": true,
    "cacheAgeSeconds": 45,
    "dataSource": "CACHE",
    "timestamp": "2024-05-27T10:30:00Z",
    "city": "New York",
    "routeId": "M1",
    "offlineMode": false,
    "provider": "NYC-MTA",
    "attribution": "Data from NYC MTA",
    "lagWarning": null
  }
}
```

## Rate Limiting (if applicable)
- Default: Not implemented
- Headers: X-RateLimit-Limit, X-RateLimit-Remaining

## HATEOAS Links Template
```json
{
  "_links": {
    "href": "http://localhost:8080/api/v1/resource"
  }
}
```
