@arrivals @regression
Feature: Transport API - Arrival Predictions

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get arrival predictions for a stop
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data is not null
    And response.metadata.cached in [true, false]
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'STALE_CACHE', 'MOCK', 'OFFLINE']

  @positive
  Scenario: Get arrivals with route filter
    Given path '/transport/arrivals'
    And param stopId = '40001'
    And param routeId = 'M1'
    When method get
    Then status 200
    And response.data[*].routeId contains 'M1'
    And response.data[*].stopId contains '40001'

  @positive
  Scenario: Validate arrival prediction structure
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data[0].stopId == '40001'
    And response.data[0].routeId is not null
    And response.data[0].scheduledArrival is not null
    And response.data[0].predictedArrival is not null
    And response.data[0].status in ['ON_TIME', 'DELAYED', 'EARLY', 'CANCELLED', 'NO_DATA']
    And response.data[0].minutesToArrival >= 0

  @positive
  Scenario: Verify delay calculation
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data[*].delaySeconds is not null
    And response.data[*].delaySeconds >= -180
    * def delayedArrivals = response.data.filter(function(x) { return x.delaySeconds > 900 })
    * match delayedArrivals[*].status contains 'DELAYED'

  @positive
  Scenario: Check realtime status of arrivals
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data[*].realtime in [true, false]
    * print 'Realtime data available: ' + response.data[0].realtime

  @negative @validation
  Scenario: Missing required stopId parameter
    Given path '/transport/arrivals'
    When method get
    Then status 400
    And response.error contains 'stopId'

  @positive @offline
  Scenario: Get arrivals in offline mode
    Given path '/transport/arrivals'
    And param stopId = '40001'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'

  @positive @performance
  Scenario: Performance test - arrival predictions response time
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And responseTime < 3000
    * print 'Arrivals response time: ' + responseTime + ' ms'

  @positive
  Scenario: Verify occupancy status in arrivals
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data[0].occupancyStatus in ['EMPTY', 'MANY_SEATS', 'FEW_SEATS', 'STANDING_ONLY', 'CRUSHED', 'FULL']

  @positive @caching
  Scenario: Verify cached arrivals data
    # First call
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.metadata.cached == true
    * def firstCacheAge = response.metadata.cacheAgeSeconds

    # Second call should have same or slightly older cache
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.metadata.cached == true
    And response.metadata.cacheAgeSeconds >= firstCacheAge
