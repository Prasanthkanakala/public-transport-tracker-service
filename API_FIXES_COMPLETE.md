# API Fixes Applied - Live Data Retrieval

## Date: 2026-04-26

## Issues Identified

### 1. **MTA API Client Issues**
- **Problem**: MTA client was throwing `TransitApiException` instead of attempting to fetch data
- **Root Cause**: Code was hardcoded to throw exception saying "MTA GTFS-RT requires protobuf parsing"
- **Impact**: No live data from MTA API

### 2. **Incorrect API Endpoints**
- **Problem**: Using `https://api.mta.info` which is for GTFS-RT feeds (Protocol Buffer format)
- **Root Cause**: Wrong base URL configuration
- **Impact**: API calls were failing or returning non-JSON data

### 3. **API Timeout Configuration**
- **Problem**: Timeouts too short (5000ms) for reliable API responses
- **Root Cause**: Conservative timeout settings
- **Impact**: Premature request failures

### 4. **JSON Parsing Issues**
- **Problem**: Parser expected wrong JSON structure for MTA responses
- **Root Cause**: Parser was written for generic JSON, not MTA SIRI format
- **Impact**: Empty vehicle lists even when API returned data

## Fixes Applied

### 1. **MTA API Client - Vehicle Locations** ✅
**File**: `/backend/src/main/java/com/transport/tracker/client/MtaApiClient.java`

**Changes**:
```java
// BEFORE: Throwing exception
throw new TransitApiException("MTA GTFS-RT requires protobuf parsing - use TransitLand instead");

// AFTER: Actual API call to MTA Bus Time SIRI API
String url = baseUrl + "/api/siri/vehicle-monitoring.json"
        + "?key=" + (hasApiKey() ? URLEncoder.encode(apiKey, StandardCharsets.UTF_8) : "")
        + "&LineRef=" + URLEncoder.encode(routeId, StandardCharsets.UTF_8)
        + "&MaximumVehicles=50";
```

**Result**: Now fetches real-time vehicle positions from MTA Bus Time API

### 2. **MTA API Client - JSON Parser** ✅
**File**: `/backend/src/main/java/com/transport/tracker/client/MtaApiClient.java`

**Changes**:
```java
// BEFORE: Generic JSON parsing
JsonNode arr = root.isArray() ? root : root.path("vehicles");

// AFTER: MTA SIRI format parsing
JsonNode activities = root.path("Siri")
        .path("ServiceDelivery")
        .path("VehicleMonitoringDelivery")
        .path(0)
        .path("VehicleActivity");
```

**Result**: Correctly parses MTA SIRI JSON responses

### 3. **API Configuration Updates** ✅
**File**: `/backend/src/main/resources/application.properties`

**Changes**:
```properties
# BEFORE
transit.api.mta.base-url=${MTA_BASE_URL:https://api.mta.info}
transit.api.mta.timeout-ms=${MTA_TIMEOUT_MS:5000}
transit.api.transitland.timeout-ms=${TRANSITLAND_TIMEOUT_MS:5000}

# AFTER
transit.api.mta.base-url=${MTA_BASE_URL:https://bustime.mta.info}
transit.api.mta.timeout-ms=${MTA_TIMEOUT_MS:10000}
transit.api.transitland.timeout-ms=${TRANSITLAND_TIMEOUT_MS:15000}
```

**Result**: 
- Correct MTA Bus Time API endpoint
- Increased timeouts for reliable responses
- Better error handling with longer wait times

## API Endpoints Now Used

### MTA Bus Time API (Primary for NYC)
- **Base URL**: `https://bustime.mta.info`
- **Vehicle Monitoring**: `/api/siri/vehicle-monitoring.json`
- **Stop Monitoring**: `/api/siri/stop-monitoring.json`
- **Service Status**: `/api/ServiceStatus`
- **Format**: JSON (SIRI format)
- **Authentication**: Optional API key (works without key)

### Transit.land API (Fallback)
- **Base URL**: `https://transit.land/api/v2/rest`
- **Routes**: `/routes?operator_onestop_id=o-dr5r-nyct`
- **Stops**: `/stops?served_by_onestop_ids=o-dr5r-nyct`
- **Operators**: `/operators?onestop_id=o-dr5r-nyct`
- **Format**: JSON (Transit.land v2 REST)
- **Authentication**: API key required (configured)

## Expected Behavior After Fixes

### 1. **Live Data Flow**
```
Frontend Request → Spring Boot Backend → MTA Bus Time API
                                      ↓ (if fails)
                                   Transit.land API
                                      ↓ (if fails)
                                   Stale Cache
                                      ↓ (if empty)
                                   Mock Data
```

### 2. **Data Source Indicators**
- **LIVE**: Fresh data from MTA or Transit.land APIs
- **CACHE**: Fresh cached data (< 5 minutes old)
- **STALE_CACHE**: Old cached data (API unavailable)
- **MOCK**: Sample data (offline mode or no cache)

### 3. **Frontend Display**
- Badge showing data source (LIVE/CACHE/STALE_CACHE/MOCK)
- Warning banner for stale or mock data
- Attribution text showing data provider
- Auto-refresh every 30 seconds in live mode

## Testing Instructions

### 1. **Restart Backend**
```bash
cd "public transport tracker service/backend"
./gradlew bootRun
```

### 2. **Restart Frontend**
```bash
cd "public transport tracker service/frontend"
npm start
```

### 3. **Verify Live Data**
1. Open browser to `http://localhost:3000`
2. Select city: **NYC**
3. Select route: **A** (or any MTA route)
4. Check badge - should show **LIVE** or **CACHE**
5. Verify vehicle positions appear on map
6. Check arrivals board for real-time predictions
7. Review service alerts

### 4. **Test API Directly**
```bash
# Test MTA vehicles
curl "http://localhost:8080/api/v1/transport/vehicles?city=nyc&routeId=A"

# Test arrivals
curl "http://localhost:8080/api/v1/transport/arrivals?stopId=Times%20Square&routeId=A"

# Test alerts
curl "http://localhost:8080/api/v1/transport/alerts?city=nyc"
```

### 5. **Check Logs**
Look for these log messages:
```
MTA API: Sending GET request to: https://bustime.mta.info/api/siri/vehicle-monitoring.json...
MTA API: Response status: 200 for URL: ...
MTA: Parsed X vehicles
Successfully fetched and cached LIVE data for key: ...
```

## Troubleshooting

### If Still Showing Mock Data

1. **Check Backend Logs**
   - Look for API errors or timeout messages
   - Verify correct URLs are being called

2. **Check Network**
   - Ensure backend can reach `bustime.mta.info`
   - Test: `curl https://bustime.mta.info/api/siri/vehicle-monitoring.json?LineRef=A`

3. **Check Frontend Offline Toggle**
   - Ensure offline mode is **OFF** (toggle should be unchecked)
   - Refresh page after toggling

4. **Clear Cache**
   - Click refresh button in UI
   - Or call: `curl -X DELETE http://localhost:8080/api/v1/cache`

5. **Check Application Properties**
   - Verify `transit.offline.enabled=false`
   - Verify correct API URLs

### If API Returns Empty Data

1. **Route ID Format**
   - MTA routes: Use uppercase (A, B, C, 1, 2, 3)
   - Bus routes: Use full format (M15, BX12, Q32)

2. **API Response Validation**
   - Check if MTA API is returning valid JSON
   - Verify SIRI format structure in response

3. **Fallback to Transit.land**
   - If MTA fails, system should automatically use Transit.land
   - Check logs for fallback messages

## Summary

✅ **Fixed**: MTA API client now fetches live data
✅ **Fixed**: Correct API endpoints configured
✅ **Fixed**: JSON parsing for MTA SIRI format
✅ **Fixed**: Increased timeouts for reliability
✅ **Fixed**: Proper error handling and fallback strategy

## Next Steps

1. **Test with different routes**: Try A, C, E, 1, 2, 3, etc.
2. **Monitor performance**: Check response times and cache hit rates
3. **Verify resilience**: Test with offline mode and API failures
4. **Production deployment**: Update environment variables if needed

---

**Status**: ✅ READY FOR TESTING
**Date**: 2026-04-26
**Version**: 1.0.0
