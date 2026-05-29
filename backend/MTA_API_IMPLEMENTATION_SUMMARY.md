# MTA API Live Data Implementation - Summary

## Overview
Successfully implemented live data fetching from MTA's GTFS-Realtime API feeds with protobuf parsing support.

## Changes Made

### 1. Dependencies Added (build.gradle)
```gradle
// GTFS Realtime for MTA protobuf parsing
implementation 'org.mobilitydata:gtfs-realtime-bindings:0.0.8'
implementation 'com.google.protobuf:protobuf-java:3.25.1'
```

### 2. MtaApiClient.java - Complete Rewrite

#### New Imports
- Added GTFS-Realtime protobuf classes:
  - `com.google.transit.realtime.GtfsRealtime.*`
  - `FeedMessage`, `FeedEntity`, `VehiclePosition`, `TripUpdate`, `Alert`

#### Updated Methods

**fetchVehicleLocations()**
- Now fetches from: `https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/{feedId}`
- Parses protobuf FeedMessage using `FeedMessage.parseFrom()`
- Calls `parseVehiclePositions()` to convert to domain models
- Returns live vehicle location data

**fetchArrivalPredictions()**
- Fetches GTFS-RT Trip Updates feed
- Parses protobuf data for arrival predictions
- Calls `parseTripUpdates()` to extract stop time updates
- Returns real-time arrival predictions

**fetchServiceAlerts()**
- Fetches from: `https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/camsys%2Fsubway-alerts`
- Parses GTFS-RT Alert feed
- Calls `parseServiceAlerts()` to extract service disruptions
- Returns active service alerts

#### New Helper Methods

**sendGetProtobuf(String url)**
- HTTP client for protobuf responses
- Sets Accept header to `application/x-protobuf`
- Returns `HttpResponse<InputStream>` for binary data
- Handles API key authentication (optional)

**parseVehiclePositions(FeedMessage feed, String routeId)**
- Extracts vehicle positions from GTFS-RT feed
- Maps to `VehicleLocation` domain model
- Includes: lat/lon, bearing, speed, occupancy status, current stop
- Filters by route ID if specified

**parseTripUpdates(FeedMessage feed, String stopId, String routeId)**
- Extracts trip updates and stop time predictions
- Maps to `ArrivalPrediction` domain model
- Calculates delay, minutes to arrival, status (ON_TIME/DELAYED/EARLY)
- Filters by stop ID and route ID

**parseServiceAlerts(FeedMessage feed)**
- Extracts service alerts from GTFS-RT feed
- Maps to `ServiceAlert` domain model
- Includes: header, description, cause, effect, affected routes
- Maps severity based on effect type

**mapRouteToFeedId(String routeId)**
- Maps MTA route IDs to their corresponding GTFS-RT feed IDs
- Supports:
  - Subway lines (A/C/E, B/D/F/M, G, J/Z, N/Q/R/W, L, 1-7, S)
  - Rail (LIRR, Metro-North, Staten Island Railway)
  - Buses (Bronx, Brooklyn, Manhattan, Queens, Staten Island)

**mapAlertSeverity(String effect)**
- Maps GTFS-RT alert effects to severity levels (HIGH/MEDIUM/LOW)
- NO_SERVICE, REDUCED_SERVICE, SIGNIFICANT_DELAYS → HIGH
- DETOUR, MODIFIED_SERVICE, OTHER_EFFECT → MEDIUM
- Others → LOW

### 3. Test Fix (TransportServiceTest.java)
- Removed unnecessary stubbing of `septaClient.isAvailable()` in Stage 3 test
- Fixed `UnnecessaryStubbingException` error
- All 34 tests now pass successfully

## MTA GTFS-RT Feed Structure

### Feed IDs Used
- **Subway Feeds**: `nyct%2Fgtfs-ace`, `nyct%2Fgtfs-bdfm`, `nyct%2Fgtfs-g`, etc.
- **Bus Feeds**: `mta%2Fgtfs-bus-bronx`, `mta%2Fgtfs-bus-brooklyn`, etc.
- **Rail Feeds**: `lirr%2Fgtfs-lirr`, `mnr%2Fgtfs-mnr`
- **Alerts Feed**: `camsys%2Fsubway-alerts`

### Data Format
- **Protocol**: GTFS-Realtime (Protocol Buffers)
- **Endpoint**: `https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/`
- **Authentication**: Optional API key via `x-api-key` header
- **Public Access**: All feeds are publicly accessible without authentication

## Build & Test Results

### Build Status
✅ **BUILD SUCCESSFUL** in 32s
- All dependencies downloaded successfully
- Code compiled without errors
- All 34 tests passed

### Test Results
```
✓ InMemoryCache: 10/10 tests passed
✓ TransportController: 5/5 tests passed
✓ AlertService: 11/11 tests passed
✓ TransportService: 8/8 tests passed (including fixed Stage 3 test)
```

## Application Status
🚀 **Application Running** on port 8080
- Spring Boot started successfully
- MTA API client initialized with GTFS-RT support
- Ready to fetch live data from MTA feeds

## API Endpoints Available

### Get Live Vehicle Locations
```
GET /api/v1/transport/vehicles?city=nyc&routeId=A
```

### Get Live Arrival Predictions
```
GET /api/v1/transport/arrivals?stopId=A42&routeId=A
```

### Get Service Alerts
```
GET /api/v1/transport/alerts?city=nyc
```

## Fallback Strategy

The application implements a 5-stage fallback chain:
1. **Offline Mode** → MOCK data
2. **Cache Hit** → CACHE data
3. **Live API Success** → LIVE data (now working with MTA GTFS-RT!)
4. **Live API Failure + Stale Cache** → STALE_CACHE data
5. **Live API Failure + No Cache** → MOCK data

## Key Features

✅ Real-time vehicle positions with lat/lon coordinates
✅ Live arrival predictions with delay information
✅ Service alerts with severity levels
✅ Automatic route-to-feed mapping
✅ Protobuf parsing with GTFS-Realtime bindings
✅ Occupancy status (crowding information)
✅ Error handling with graceful degradation
✅ Caching for performance optimization

## Next Steps (Optional Enhancements)

1. **Add MTA API Key**: Set environment variable `MTA_API_KEY` for higher rate limits
2. **GTFS Static Data**: Load static GTFS data for stop names, route details
3. **Real-time Monitoring**: Add metrics for API response times
4. **Rate Limiting**: Implement rate limiting for MTA API calls
5. **WebSocket Support**: Stream real-time updates to frontend

## Technical Notes

- **Java Version**: 21.0.7
- **Gradle Version**: 8.7
- **Spring Boot**: 3.2.3
- **GTFS-Realtime Bindings**: 0.0.8
- **Protobuf Java**: 3.25.1

## Testing the Implementation

To test live MTA data:

```bash
# Start the application
cd backend
gradle bootRun

# In another terminal, test the API
curl "http://localhost:8080/api/v1/transport/vehicles?city=nyc&routeId=A"
```

Expected response will contain live vehicle positions from MTA's GTFS-RT feed!

---

**Implementation Date**: April 27, 2026
**Status**: ✅ Complete and Tested
**Build Status**: ✅ All tests passing
**Application Status**: 🚀 Running successfully
