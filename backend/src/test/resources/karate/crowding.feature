@crowding @regression
Feature: Transport API - Crowding Information

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get crowding information for a route
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data is not null

  @positive
  Scenario: Validate crowding data structure
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def crowding = response.data.crowding[0]
    And crowding.vehicleId is not null
    And crowding.routeId == 'M1'
    And crowding.capacity >= 0
    And crowding.currentPassengers >= 0
    And crowding.currentPassengers <= crowding.capacity
    And crowding.occupancyPercentage >= 0 && crowding.occupancyPercentage <= 100

  @positive
  Scenario: Verify crowding levels
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.crowding[*].level in ['LOW', 'MEDIUM', 'HIGH', 'FULL']
    * def fullVehicles = response.data.crowding.filter(function(x) { return x.level == 'FULL' })
    * print 'Full vehicles: ' + fullVehicles.length

  @positive
  Scenario: Verify GTFS occupancy status
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.crowding[*].gtfsOccupancyStatus in ['EMPTY', 'MANY_SEATS', 'FEW_SEATS', 'STANDING_ONLY', 'CRUSHED', 'FULL']

  @positive
  Scenario: Validate occupancy percentage calculation
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def crowding = response.data.crowding[0]
    * def calculatedPercentage = (crowding.currentPassengers / crowding.capacity) * 100
    And Math.abs(crowding.occupancyPercentage - calculatedPercentage) < 1

  @positive @filtering
  Scenario: Verify route-specific crowding data
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def allM1 = response.data.crowding.filter(function(x) { return x.routeId != 'M1' })
    And allM1.length == 0

  @negative @validation
  Scenario: Missing required routeId parameter
    Given path '/transport/crowding'
    And param city = 'New York'
    When method get
    Then status 400
    And response.error contains 'routeId'

  @positive @offline
  Scenario: Get crowding in offline mode
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true

  @positive @performance
  Scenario: Performance - crowding endpoint
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And responseTime < 3000

  @positive @alerts
  Scenario: Verify crowding-related alert generation
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def fullVehicles = response.data.crowding.filter(function(x) { return x.level == 'FULL' })
    * print 'Full vehicles count: ' + fullVehicles.length
    And response.data.alerts is not null

  @positive
  Scenario: Verify metadata in crowding response
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.routeId == 'M1'
    And response.metadata.city == 'New York'
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'MOCK']

  @positive @caching
  Scenario: Verify crowding data caching
    # First request
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def firstData = response

    # Second request
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.cached == firstData.metadata.cached

  @positive
  Scenario: Verify capacity data availability
    Given path '/transport/crowding'
    And param routeId = 'M1'
    And param city = 'New York'
    When method get
    Then status 200
    * def capacityData = response.data.crowding.filter(function(x) { return x.capacity > 0 })
    * print 'Vehicles with capacity info: ' + capacityData.length
