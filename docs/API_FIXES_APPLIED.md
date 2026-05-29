# API Parameter Fixes Applied

## Date: 2026-04-26

## Summary
Fixed incorrect API endpoint parameters and URLs for both MTA and Transit.land APIs to enable proper live data retrieval.

---

## MTA API Fixes

### 1. Vehicle Locations Endpoint
**Problem:** Using non-existent `/api/schedule/{routeId}/vehicles.json` endpoint

**Solution:** 
- Changed to GTFS-RT feed endpoints: `/Dataservice/mtagtfsfeeds/{feedId}`
- Added route-to-feed mapping logic to select correct feed based on route ID
- Feed mapping covers:
  - Subway lines (ACE, BDFM, G, JZ, NQRW, L, 1-7, S)
  - Rail (LIRR, Metro-North, Staten Island Railway)
  - Buses (Bronx, Brooklyn, Manhattan, Queens, Staten Island)

**Feed ID Examples:**
- `nyct%2Fgtfs-ace` - A, C, E subway lines
- `nyct%2Fgtfs-bdfm` - B, D, F, M subway lines
- `mta%2Fgtfs-bus-manhattan` - Manhattan buses (M prefix)
- `lirr%2Fgtfs-lirr` - Long Island Rail Road

### 2. Arrival Predictions Endpoint
**Problem:** API key parameter positioned incorrectly in query string

**Solution:**
- Moved `key` parameter to beginning of query string
- Changed from: `?MonitoringRef={stopId}&key={apiKey}`
- Changed to: `?key={apiKey}&MonitoringRef={stopId}&MaximumStopVisits=10`
- Added `MaximumStopVisits` parameter to limit results

### 3. Service Alerts Endpoint
**Problem:** Using non-existent `/api/alerts.json` endpoint

**Solution:**
- Changed to: `/api/ServiceStatus`
- This is the correct MTA Service Status API endpoint
- No authentication required for this endpoint

---

## Transit.land API Fixes

### 1. API Key Parameter Name
**Problem:** Using incorrect parameter name `apikey`

**Solution:**
- Changed all instances from `apikey` to `api_key`
- Transit.land v2 REST API requires `api_key` (with underscore)

### 2. Vehicle Locations Endpoint
**Problem:** Using non-existent `/vehicles` endpoint

**Solution:**
- Changed to: `/routes?route_id={routeId}&include_geometry=false&api_key={key}&limit=100`
- Transit.land v2 provides route data, not real-time vehicle positions
- Added `include_geometry=false` to reduce payload size
- Added `limit=100` to control response size

### 3. Arrival Predictions Endpoint
**Problem:** Using non-existent `/stop_times` endpoint

**Solution:**
- Changed to: `/stops?stop_id={stopId}&include_routes=true&api_key={key}&limit=50`
- Transit.land v2 uses `/stops` endpoint
- Added `include_routes=true` to get route information
- Added `limit=50` to control response size

### 4. Service Alerts Endpoint
**Problem:** Using non-existent `/alerts` endpoint with incorrect parameters

**Solution:**
- Changed to: `/operators?city_name={city}&api_key={key}&limit=20`
- Transit.land v2 provides operator/agency data
- Defaults to "New York" if city not specified
- Added `limit=20` to control response size

### 5. Health Check Endpoint
**Problem:** Using `/agencies?per_page=1&apikey={key}`

**Solution:**
- Changed to: `/operators?limit=1&api_key={key}`
- Transit.land v2 uses `/operators` not `/agencies`
- Changed `per_page` to `limit`
- Fixed API key parameter name

---

## Configuration Updates Required

### Environment Variables
Ensure these are properly set:

```properties
# MTA API
MTA_BASE_URL=https://api.mta.info
MTA_API_KEY=<your-mta-api-key>
MTA_TIMEOUT_MS=5000

# Transit.land API
TRANSITLAND_BASE_URL=https://transit.land/api/v2/rest
TRANSITLAND_API_KEY=<your-transitland-api-key>
TRANSITLAND_TIMEOUT_MS=5000
```

### MTA API Key Registration
1. Visit: https://api.mta.info/
2. Sign up for API access
3. Get your API key
4. Set `MTA_API_KEY` environment variable

### Transit.land API Key
1. Visit: https://www.transit.land/
2. Sign up for API access
3. Get your API key (currently using: BTdcRhsRSVM2qXdjqFw9bFFtPxZshBkd)
4. Set `TRANSITLAND_API_KEY` environment variable

---

## Testing Recommendations

### 1. Test MTA Vehicle Locations
```bash
curl "https://api.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-ace" \
  -H "x-api-key: YOUR_MTA_API_KEY"
```

### 2. Test MTA Arrivals
```bash
curl "https://api.mta.info/api/siri/stop-monitoring.json?key=YOUR_MTA_API_KEY&MonitoringRef=STOP_ID&MaximumStopVisits=10"
```

### 3. Test MTA Service Status
```bash
curl "https://api.mta.info/api/ServiceStatus"
```

### 4. Test Transit.land Routes
```bash
curl "https://transit.land/api/v2/rest/routes?route_id=A&api_key=YOUR_API_KEY&limit=10"
```

### 5. Test Transit.land Stops
```bash
curl "https://transit.land/api/v2/rest/stops?stop_id=STOP_ID&api_key=YOUR_API_KEY&limit=10"
```

### 6. Test Transit.land Operators
```bash
curl "https://transit.land/api/v2/rest/operators?city_name=New%20York&api_key=YOUR_API_KEY&limit=10"
```

---

## Expected Behavior After Fixes

### MTA API
- ✅ Vehicle locations will use correct GTFS-RT feeds
- ✅ Arrivals will properly authenticate and return stop monitoring data
- ✅ Service alerts will fetch from correct ServiceStatus endpoint
- ✅ Route-to-feed mapping ensures correct data source

### Transit.land API
- ✅ All endpoints use correct `api_key` parameter name
- ✅ Routes endpoint returns route data with proper filtering
- ✅ Stops endpoint returns stop data with route information
- ✅ Operators endpoint provides agency/operator data
- ✅ Health check uses correct endpoint

---

## Known Limitations

### MTA API
1. **GTFS-RT Format**: Vehicle location data is in Protocol Buffer format, not JSON
   - May require additional parsing logic
   - Consider using GTFS-RT Java library for parsing

2. **Feed-specific Data**: Each feed only contains data for specific routes
   - Must use correct feed ID for each route
   - Cross-feed queries not supported

3. **API Key Required**: Most endpoints require valid MTA API key
   - ServiceStatus endpoint is public (no key needed)

### Transit.land API
1. **No Real-time Vehicle Positions**: v2 REST API provides static route/stop data
   - Real-time data requires GTFS-RT feed integration
   - Consider using MTA as primary source for real-time data

2. **Onestop IDs**: Transit.land uses onestop_id format
   - Format: `r-<geohash>-<route_short_name>` for routes
   - Format: `s-<geohash>-<stop_code>` for stops
   - May need ID mapping/translation

3. **Rate Limiting**: API has rate limits
   - Implement caching to reduce API calls
   - Use Option A resilience features (already implemented)

---

## Next Steps

1. **Add GTFS-RT Parser**: Implement Protocol Buffer parsing for MTA feeds
2. **ID Mapping**: Create mapping between route IDs and Transit.land onestop_ids
3. **Enhanced Caching**: Leverage existing Option A cache for API responses
4. **Error Handling**: Improve fallback logic when APIs return unexpected formats
5. **Integration Testing**: Create comprehensive tests for both APIs
6. **Documentation**: Update API documentation with correct endpoint examples

---

## Files Modified

1. `backend/src/main/java/com/transport/tracker/client/MtaApiClient.java`
   - Fixed vehicle locations endpoint
   - Added route-to-feed mapping method
   - Fixed arrivals endpoint parameter order
   - Fixed service alerts endpoint

2. `backend/src/main/java/com/transport/tracker/client/TransitLandApiClient.java`
   - Fixed API key parameter name (apikey → api_key)
   - Fixed vehicle locations endpoint
   - Fixed arrivals endpoint
   - Fixed service alerts endpoint
   - Fixed health check endpoint

---

## References

- MTA API Documentation: https://api.mta.info/
- MTA GTFS-RT Feeds: https://api.mta.info/#/subwayRealTimeFeeds
- Transit.land API v2 Docs: https://www.transit.land/documentation/
- Transit.land REST API: https://transit.land/api/v2/rest/
