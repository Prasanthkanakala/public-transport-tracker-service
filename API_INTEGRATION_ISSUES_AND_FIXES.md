# Public Transport Tracker - API Integration Issues & Fixes

## 🔴 Critical Issues Identified

### Issue 1: Missing Method Parameter in `selectClient()` Call
**Location**: `TransportService.java` line 96

**Problem**:
```java
TransitApiClient selectedClient = selectClient(); // ❌ Missing city parameter
```

The `selectClient(String city)` method requires a `city` parameter, but it's being called without any arguments. This will cause a compilation error.

**Fix**:
```java
TransitApiClient selectedClient = selectClient(city); // ✅ Pass city parameter
```

---

### Issue 2: MTA API Endpoints Are Incorrect
**Location**: `MtaApiClient.java`

**Problem**:
The MTA API endpoints used in the code don't match the actual MTA API structure:

```java
// ❌ These endpoints don't exist in MTA API:
String url = baseUrl + "/api/schedule/" + encodedRoute + "/vehicles.json";
String url = baseUrl + "/api/siri/stop-monitoring.json";
String url = baseUrl + "/api/alerts.json";
```

**Root Cause**:
The MTA API uses **GTFS-Realtime protocol buffers**, not JSON REST endpoints. The actual MTA API structure is:
- Real-time feeds are in Protocol Buffer format (.pb files)
- Requires parsing GTFS-RT messages
- Different feed IDs for different subway lines

**Actual MTA API Endpoints**:
```
https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-ace
https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-bdfm
https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs
```

**Why It's Not Working**:
1. The endpoints return 404 (Not Found) because they don't exist
2. MTA requires Protocol Buffer parsing, not JSON
3. The API key header should be `x-api-key`, which is correct in the code

---

### Issue 3: Transit.land API Endpoints May Be Incorrect
**Location**: `TransitLandApiClient.java`

**Problem**:
```java
// ❌ These endpoints may not exist or require different parameters:
String url = baseUrl + "/vehicles?route_id=" + encode(routeId);
String url = baseUrl + "/stop_times?stop_id=" + encode(stopId);
String url = baseUrl + "/alerts?apikey=" + encode(apiKey);
```

**Root Cause**:
Transit.land API v2 REST structure:
- `/routes` - Returns route information
- `/stops` - Returns stop information  
- `/operators` - Returns transit operators
- **Does NOT have** `/vehicles` endpoint for real-time data
- **Does NOT have** `/stop_times` for real-time arrivals
- **Does NOT have** `/alerts` endpoint

**Actual Transit.land Capabilities**:
- Transit.land v2 REST API is primarily for **static GTFS data** (schedules, routes, stops)
- Real-time data requires GTFS-Realtime feeds from individual operators
- The API is more of a directory/catalog than a real-time data provider

---

### Issue 4: API Key May Be Invalid or Rate-Limited
**Location**: `application.properties`

```properties
transit.api.transitland.api-key=${TRANSITLAND_API_KEY:BTdcRhsRSVM2qXdjqFw9bFFtPxZshBkd}
```

**Potential Issues**:
1. The API key might be a demo/example key with limited access
2. Rate limiting may be blocking requests
3. The key might not have permissions for the endpoints being called

---

## ✅ Recommended Solutions

### Solution 1: Fix the `selectClient()` Method Call (IMMEDIATE)

**File**: `/backend/src/main/java/com/transport/tracker/service/TransportService.java`

**Change line 96** from:
```java
TransitApiClient selectedClient = selectClient();
```

To:
```java
TransitApiClient selectedClient = selectClient(city);
```

**Also fix line 143** in `getArrivals()` method:
```java
TransitApiClient client = selectClient(); // ❌
```

To:
```java
TransitApiClient client = selectClient(null); // ✅ or pass appropriate city
```

**Also fix line 172** in `getServiceAlerts()` method:
```java
TransitApiClient client = selectClient(); // ❌
```

To:
```java
TransitApiClient client = selectClient(city); // ✅
```

**Also fix line 205** in `getRoutePlans()` method:
```java
TransitApiClient client = selectClient(); // ❌
```

To:
```java
TransitApiClient client = selectClient(city); // ✅
```

---

### Solution 2: Implement Proper MTA API Integration (RECOMMENDED)

**Option A**: Use MTA's Official GTFS-Realtime Feeds

1. Add GTFS-Realtime library dependency to `build.gradle`:
```gradle
implementation 'com.google.transit:gtfs-realtime-bindings:0.0.8'
```

2. Rewrite `MtaApiClient` to parse Protocol Buffer feeds
3. Use correct feed URLs with proper API key authentication

**Option B**: Use MTA's Bus Time API (JSON-based)

MTA Bus Time API provides JSON responses:
```
http://bustime.mta.info/api/siri/vehicle-monitoring.json?key=YOUR_KEY&VehicleMonitoringDetailLevel=calls
http://bustime.mta.info/api/siri/stop-monitoring.json?key=YOUR_KEY&MonitoringRef=STOP_ID
```

**Option C**: Switch to Mock Data for Demo (QUICKEST)

If this is for demonstration purposes, enable offline mode:
```properties
transit.offline.enabled=true
```

---

### Solution 3: Fix Transit.land API Integration

**Option A**: Use Only Static Data from Transit.land

Modify `TransitLandApiClient` to:
1. Fetch route and stop information only
2. Return empty lists for real-time data (vehicles, arrivals, alerts)
3. Let the application fall back to mock data

**Option B**: Get a Valid API Key

1. Register at https://www.transit.land/
2. Obtain a valid API key with appropriate permissions
3. Update the `.env` file or environment variables

**Option C**: Use Transit.land's GTFS-RT Feed Directory

Transit.land can provide URLs to GTFS-RT feeds:
```
GET /api/v2/rest/feeds?spec=gtfs-rt
```

Then fetch real-time data directly from operator feeds.

---

### Solution 4: Add Comprehensive Error Logging

Add detailed logging to understand what's happening:

```java
private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
    log.info("Sending GET request to: {}", url);
    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(timeoutMs))
            .GET();
    if (hasApiKey()) {
        reqBuilder.header("x-api-key", apiKey);
        log.debug("Added API key header");
    }
    HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
    log.info("Response status: {}", response.statusCode());
    log.debug("Response body: {}", response.body());
    return response;
}
```

---

## 🔧 Quick Fix Implementation Steps

### Step 1: Fix Compilation Error (5 minutes)
1. Open `TransportService.java`
2. Fix all `selectClient()` calls to include the `city` parameter
3. Rebuild the application

### Step 2: Enable Detailed Logging (5 minutes)
1. Update `application.properties`:
```properties
logging.level.com.transport.tracker=DEBUG
logging.level.com.transport.tracker.client=DEBUG
```
2. Restart the application
3. Check logs to see actual API responses

### Step 3: Test with Mock Data (2 minutes)
1. Set `OFFLINE_MODE=true` in environment or `.env` file
2. Restart application
3. Verify that mock data is returned successfully

### Step 4: Fix API Integrations (1-2 hours)
1. Choose one of the solutions above for MTA
2. Choose one of the solutions above for Transit.land
3. Implement the changes
4. Test with real API calls

---

## 📊 Testing Recommendations

### Test 1: Verify Compilation
```bash
cd backend
./gradlew clean build
```

### Test 2: Check API Availability
```bash
# Test Transit.land (should work for static data)
curl "https://transit.land/api/v2/rest/agencies?per_page=1&apikey=YOUR_KEY"

# Test MTA (requires proper endpoint)
curl -H "x-api-key: YOUR_KEY" "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs"
```

### Test 3: Application Endpoints
```bash
# Test with offline mode
curl "http://localhost:8080/api/v1/transport?city=nyc&routeId=1&offline=true"

# Test with live mode (after fixes)
curl "http://localhost:8080/api/v1/transport?city=nyc&routeId=1"
```

---

## 📝 Summary

**Root Causes**:
1. ✅ **Compilation Error**: Missing `city` parameter in `selectClient()` calls
2. ✅ **Wrong MTA Endpoints**: Using non-existent JSON endpoints instead of GTFS-RT
3. ✅ **Wrong Transit.land Endpoints**: Trying to access real-time data from static API
4. ⚠️ **Possible API Key Issues**: May need valid keys with proper permissions

**Immediate Actions**:
1. Fix the `selectClient(city)` method calls (compilation error)
2. Enable DEBUG logging to see actual API responses
3. Test with offline mode to verify application logic works
4. Decide on API integration strategy (GTFS-RT, Bus Time API, or mock data)

**Long-term Solutions**:
1. Implement proper GTFS-Realtime parsing for MTA
2. Use Transit.land only for static data or as a feed directory
3. Add comprehensive error handling and logging
4. Implement proper API key management and rotation
