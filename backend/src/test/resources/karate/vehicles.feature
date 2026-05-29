@vehicles @regression
Feature: Transport API - Vehicle Locations

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get vehicle locations for all routes
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data is not null
    And response.data[0] is not null

  @positive
  Scenario: Get vehicle locations for specific route
    Given path '/transport/vehicles'
    And param city = 'New York'
    And param routeId = 'M1'
    When method get
    Then status 200
    And response.data[*].routeId contains 'M1'

  @positive
  Scenario: Validate vehicle location structure
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    * def vehicle = response.data[0]
    And vehicle.vehicleId is not null
    And vehicle.routeId is not null
    And vehicle.tripId is not null
    And vehicle.latitude is not null
    And vehicle.longitude is not null
    And vehicle.latitude >= -90 && vehicle.latitude <= 90
    And vehicle.longitude >= -180 && vehicle.longitude <= 180
    And vehicle.bearing >= 0 && vehicle.bearing <= 360
    And vehicle.speedKmh >= 0

  @positive
  Scenario: Verify vehicle status values
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data[*].status in ['IN_TRANSIT_TO', 'STOPPED_AT', 'INCOMING_AT']
    * print 'Vehicle statuses verified: ' + response.data[0].status

  @positive
  Scenario: Verify delay information in vehicles
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data[0].delaySeconds is not null
    And response.data[0].delaySeconds >= -300
    * def delayedVehicles = response.data.filter(function(x) { return x.delaySeconds > 300 })
    * print 'Delayed vehicles count: ' + delayedVehicles.length

  @positive
  Scenario: Check occupancy status in vehicles
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data[0].occupancyStatus in ['EMPTY', 'MANY_SEATS', 'FEW_SEATS', 'STANDING_ONLY', 'CRUSHED', 'FULL']

  @positive @realtime
  Scenario: Verify realtime vehicle data accuracy
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'MOCK']
    * print 'Data source: ' + response.metadata.dataSource

  @positive @performance
  Scenario: Performance - vehicle locations endpoint
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And responseTime < 4000
    * print 'Vehicles endpoint response time: ' + responseTime + ' ms'

  @positive @offline
  Scenario: Get vehicle locations in offline mode
    Given path '/transport/vehicles'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true

  @positive @filtering
  Scenario: Verify route-based vehicle filtering
    Given path '/transport/vehicles'
    And param city = 'New York'
    And param routeId = 'M1'
    When method get
    Then status 200
    * def allM1Vehicles = response.data.filter(function(x) { return x.routeId != 'M1' })
    * match allM1Vehicles length == 0

  @positive @metadata
  Scenario: Verify vehicle metadata completeness
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.city is not null
    And response.metadata.provider is not null
    And response.metadata.timestamp is not null
    And response.metadata.cached in [true, false]

  @positive
  Scenario: Verify GPS accuracy and precision
    Given path '/transport/vehicles'
    And param city = 'New York'
    When method get
    Then status 200
    * def vehicle = response.data[0]
    * def latString = vehicle.latitude.toString()
    * def lonString = vehicle.longitude.toString()
    And latString contains '.'
    And lonString contains '.'
    * print 'Vehicle GPS: ' + vehicle.latitude + ', ' + vehicle.longitude
