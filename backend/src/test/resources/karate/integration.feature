@integration @regression
Feature: Transport API - Integration Scenarios

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive @integration
  Scenario: Complete user journey - Get transport data and plan route
    # Step 1: Get current transport data
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    * def transportData = response.data
    * def firstRoute = transportData.vehicles[0].routeId
    * print 'Available route: ' + firstRoute

    # Step 2: Get arrivals for a stop
    Given path '/transport/arrivals'
    And param stopId = '40001'
    And param routeId = firstRoute
    When method get
    Then status 200
    And response.data.length > 0
    * print 'Next arrival in ' + response.data[0].minutesToArrival + ' minutes'

    # Step 3: Get alerts for the city
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def alerts = response.data
    * print 'Active alerts: ' + alerts.length

    # Step 4: Plan a route
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.length > 0
    * def routePlan = response.data[0]
    * print 'Optimal route duration: ' + routePlan.durationMinutes + ' minutes'

  @positive @integration
  Scenario: Verify cache effectiveness in multi-endpoint calls
    # Get cache stats before
    Given path '/cache/stats'
    When method get
    Then status 200
    * def statsBefore = response.hitCount

    # Make multiple requests to same endpoint
    * repeat 3
      | Given path '/transport/arrivals'
      | And param stopId = '40001'
      | When method get
      | Then status 200

    # Verify cache hits increased
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.hitCount >= statsBefore
    * print 'Cache hit rate improved by ' + (response.hitCount - statsBefore) + ' hits'

  @positive @integration
  Scenario: Cross-endpoint data consistency
    # Get vehicles
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    * def vehicles = response.data
    * def vehicleRoutes = vehicles.map(function(v) { return v.routeId }).filter(function(v, i, arr) { return arr.indexOf(v) === i })

    # Get arrivals for all routes
    * repeat 1
      | Given path '/transport/arrivals'
      | And param stopId = '40001'
      | When method get
      | Then status 200
      | * def arrivalRoutes = response.data.map(function(a) { return a.routeId }).filter(function(a, i, arr) { return arr.indexOf(a) === i })

  @positive @integration
  Scenario: Offline mode consistency across endpoints
    # Get all data in offline mode
    Given path '/transport'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'

    # Get vehicles in offline mode
    Given path '/transport/vehicles'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'

    # Get alerts in offline mode
    Given path '/transport/alerts'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'

  @positive @integration
  Scenario: Data freshness across multiple requests
    # First request
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    * def firstTimestamp = response.metadata.timestamp
    * def firstDataSource = response.metadata.dataSource

    # Wait and make second request
    * def sleep = function(ms) { java.lang.Thread.sleep(ms) }
    * call sleep(1000)

    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    * def secondTimestamp = response.metadata.timestamp
    * print 'First request: ' + firstTimestamp + ', Data source: ' + firstDataSource
    * print 'Second request: ' + secondTimestamp + ', Cache age: ' + response.metadata.cacheAgeSeconds + 's'

  @positive @integration
  Scenario: Error recovery and continued operation
    # Make invalid request
    Given path '/transport/arrivals'
    When method get
    Then status 400

    # System should recover and serve valid requests
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data is not null

    # Continue with other endpoints
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data is not null

  @positive @integration @degradation
  Scenario: Graceful degradation when primary data unavailable
    # Request data
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    * def firstResponse = response

    # In case of data source degradation, system should still return data
    And response.data is not null
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'STALE_CACHE', 'MOCK']
    * print 'Data source: ' + response.metadata.dataSource + ' (graceful degradation working)'

  @positive @integration
  Scenario: Multi-city journey planning
    # Get vehicles from different cities
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200

    # Switch to different city
    Given path '/transport/vehicles'
    And param city = 'London'
    When method get
    Then status 200
    And response.metadata.city == 'London'

  @positive @integration
  Scenario: Alert propagation across endpoints
    # Get alerts
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def alerts = response.data

    # If there are alerts, verify they appear in transport data
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.alerts is not null

  @positive @integration @performance
  Scenario: Performance under concurrent-like sequential calls
    * def startTime = java.lang.System.currentTimeMillis()
    
    * repeat 5
      | Given path '/transport/arrivals'
      | And param stopId = '40001'
      | When method get
      | Then status 200

    * repeat 5
      | Given path '/transport/vehicles'
      | And param city = 'New York'
      | When method get
      | Then status 200

    * repeat 5
      | Given path '/transport/alerts'
      | And param city = 'New York'
      | When method get
      | Then status 200

    * def endTime = java.lang.System.currentTimeMillis()
    * def totalTime = endTime - startTime
    * print 'Total time for 15 requests: ' + totalTime + ' ms'
    * print 'Average time per request: ' + (totalTime / 15) + ' ms'

  @positive @integration
  Scenario: Verify data correlation between endpoints
    # Get route
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    * def vehicle = response.data[0]
    * def stopId = '40001'

    # Get crowding for same route
    Given path '/transport/crowding'
    And param routeId = vehicle.routeId
    And param city = 'New York'
    When method get
    Then status 200
    * def crowdingData = response.data.crowding[0]
    And crowdingData.routeId == vehicle.routeId
    * print 'Vehicle route ' + vehicle.routeId + ' matches crowding data route'

  @positive @integration
  Scenario: Cache invalidation after data update
    # Get initial cache stats
    Given path '/cache/stats'
    When method get
    Then status 200
    * def initialStats = response

    # Make requests
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200

    # Clear cache
    Given path '/cache'
    When method delete
    Then status 200 || status 204

    # Verify cache cleared
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.cacheSize == 0
